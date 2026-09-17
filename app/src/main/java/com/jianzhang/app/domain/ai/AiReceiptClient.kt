package com.jianzhang.app.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import javax.net.ssl.SSLException

object AiReceiptClient {
    suspend fun recognizeImage(context: Context, uri: Uri, config: AiReceiptConfig): String = withContext(Dispatchers.IO) {
        requireCloudConfig(config)
        val bytes = encodeJpeg(context, uri)
        require(bytes.size <= MAX_IMAGE_BYTES) { "图片超过 20 MB，请压缩后重试" }
        val dataUrl = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        request(config, receiptMessages(imageDataUrl = dataUrl))
    }

    suspend fun analyzeOcrText(config: AiReceiptConfig, ocrText: String): String = withContext(Dispatchers.IO) {
        requireCloudConfig(config)
        require(ocrText.isNotBlank()) { "本地 OCR 没有识别到文字" }
        request(config, receiptMessages(ocrText = ocrText))
    }

    /** Screen-understanding endpoint used only by the control-center assistant. */
    suspend fun analyzeScreenImage(context: Context, uri: Uri, config: AiReceiptConfig): String = withContext(Dispatchers.IO) {
        requireCloudConfig(config)
        val bytes = encodeJpeg(context, uri)
        require(bytes.size <= MAX_IMAGE_BYTES) { "图片超过 20 MB，请压缩后重试" }
        val dataUrl = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        request(
            config,
            screenMessages(imageDataUrl = dataUrl),
            maxTokens = 1_800,
            totalTimeoutMillis = SCREEN_REQUEST_TIMEOUT_MILLIS
        )
    }

    /** OCR-first screen-understanding endpoint; it reuses the same provider and HTTP stack. */
    suspend fun analyzeScreenText(config: AiReceiptConfig, ocrText: String): String = withContext(Dispatchers.IO) {
        requireCloudConfig(config)
        require(ocrText.isNotBlank()) { "本地 OCR 没有识别到文字" }
        request(
            config,
            screenMessages(ocrText = ocrText),
            maxTokens = 1_800,
            totalTimeoutMillis = SCREEN_REQUEST_TIMEOUT_MILLIS
        )
    }

    suspend fun testConnection(config: AiReceiptConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireCloudConfig(config.copy(enabled = true))
            val messages = if (config.mode == AiRecognitionMode.VISION) {
                connectionTestVisionMessages()
            } else {
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", "请只回复 OK")
                )
            }
            val body = JSONObject()
                .put("model", config.model.trim())
                .put("messages", messages)
                .put("max_tokens", 8)
            execute(config.copy(enabled = true), body)
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(friendlyConnectionError(error), error))
        }
    }

    internal fun completionUrl(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/')
        require(clean.startsWith("https://")) { "API 地址必须使用 HTTPS" }
        return when {
            clean.endsWith("/chat/completions") -> clean
            clean.endsWith("/responses") -> clean.removeSuffix("/responses") + "/chat/completions"
            else -> "$clean/chat/completions"
        }
    }

    private fun request(
        config: AiReceiptConfig,
        messages: JSONArray,
        maxTokens: Int = 900,
        requestJson: Boolean = true,
        totalTimeoutMillis: Long? = null
    ): String {
        val body = JSONObject()
            .put("model", config.model.trim())
            .put("messages", messages)
            .put("temperature", 0)
            .put("max_tokens", maxTokens)
        if (requestJson) body.put("response_format", JSONObject().put("type", "json_object"))
        val deadlineNanos = totalTimeoutMillis?.let { System.nanoTime() + it * 1_000_000L }
        fun executeWithinDeadline(): JSONObject {
            val remainingMillis = deadlineNanos?.let { deadline ->
                ((deadline - System.nanoTime()) / 1_000_000L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            }
            if (remainingMillis != null && remainingMillis <= 0) {
                throw SocketTimeoutException("AI request deadline exceeded")
            }
            return execute(config, body, remainingMillis)
        }
        val response = try {
            executeWithinDeadline()
        } catch (error: AiHttpException) {
            if (requestJson && error.code in setOf(400, 422)) {
                body.remove("response_format")
                try {
                    executeWithinDeadline()
                } catch (retryError: AiHttpException) {
                    if (retryError.code in setOf(400, 422)) {
                        body.remove("temperature")
                        executeWithinDeadline()
                    } else {
                        throw retryError
                    }
                }
            } else {
                throw error
            }
        }
        return extractContent(response)
    }

    private fun execute(config: AiReceiptConfig, body: JSONObject, remainingMillis: Int? = null): JSONObject {
        val connection = URL(completionUrl(config.baseUrl)).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = remainingMillis?.coerceAtMost(20_000) ?: 20_000
            connection.readTimeout = remainingMillis?.coerceAtMost(75_000) ?: 75_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val serverMessage = runCatching {
                    val json = JSONObject(responseText)
                    json.optJSONObject("error")?.optString("message")
                        ?.takeIf(String::isNotBlank)
                        ?: json.optString("message")
                }.getOrNull().orEmpty().replace(config.apiKey.trim(), "••••")
                throw AiHttpException(
                    code,
                    friendlyHttpError(code, serverMessage)
                )
            }
            JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    internal fun extractContent(response: JSONObject): String {
        val message = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: error("AI 返回中缺少 message")
        val content = message.opt("content")
        return when (content) {
            is String -> content
            is JSONArray -> buildString {
                repeat(content.length()) { index ->
                    val part = content.optJSONObject(index)
                    if (part?.optString("type") == "text") append(part.optString("text"))
                }
            }
            else -> ""
        }.takeIf(String::isNotBlank) ?: error("AI 没有返回可用内容")
    }

    private fun receiptMessages(imageDataUrl: String? = null, ocrText: String? = null): JSONArray {
        val prompt = """
            你是票据结构化识别器。请分析用户主动提供的购物小票、发票或支付账单，只提取这一次实际消费。
            返回且只返回一个 JSON 对象，字段固定为：
            amount：最终实付金额，字符串数字，不含货币符号；无法确定为 null。
            date：消费日期，YYYY-MM-DD；无法确定为 null。
            time：消费时间，HH:mm；无法确定为 null。
            merchant：实际收款商户；无法确定为 null。不要把支付机构、银行、退款提示当商户。
            category：只能是 餐饮、交通、购物、医疗、居住、水电燃气、通讯、服饰、学习、娱乐、日用、家庭、旅行、汽车、运动、宠物、订阅、人情、保险、其他 之一。
            分类必须结合商户经营类型判断；麦当劳、肯德基、星巴克、瑞幸等餐厅、快餐或饮品商户应归为餐饮，不要因为画面是订单页或取餐页就归为其他。
            payment_method：付款账户或方式；无法确定为 null。
            confidence：只能是 high、low、missing，表示关键字段整体可信度。
            raw_text：图片中的关键原文，无法读取则为空字符串。
            金额优先依据实付、合计、应付、付款金额、订单金额等语义；不要把原价、优惠、余额、订单号当金额，不得猜测缺失字段。
        """.trimIndent()
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
        if (imageDataUrl != null) {
            content.put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", imageDataUrl)
                )
            )
        } else {
            content.put(JSONObject().put("type", "text").put("text", "本地 OCR 原文：\n${ocrText.orEmpty()}"))
        }
        return JSONArray().put(JSONObject().put("role", "user").put("content", content))
    }

    private fun screenMessages(imageDataUrl: String? = null, ocrText: String? = null): JSONArray {
        val prompt = """
            你是“简记·慧屏”的屏幕理解器。只根据用户主动提供的这一张截图提取可立即执行的信息。
            一张截图可以同时产生多个动作。返回且只返回一个 JSON 对象：
            {"schemaVersion":1,"summary":string|null,"rawText":string|null,"actions":[...]}
            actions 允许的 type 仅为 expense、pickup、package、travel；每个元素必须含 type、0到1之间的 confidence 和 data。
            expense.data 字段：amount, merchant, category, date, time, paymentMethod。
            expense.category 必须结合商户经营类型判断；麦当劳、肯德基、星巴克、瑞幸等餐厅、快餐或饮品商户应归为餐饮，只有确实无法判断时才能填其他。
            pickup.data 字段：merchant, code, queueNumber, tableNumber, location, time。
            package.data 字段：company, code, location, shelf, deadline。
            travel.data 字段：travelType, number, from, to, date, departureTime, arrivalTime, terminal, gate, carriage, seat, boardingTime。
            travelType 只能是 train、flight、bus、ship、other。
            金额必须是最终实付金额，不含货币符号；日期使用 YYYY-MM-DD，时间使用 HH:mm。
            所有字段只能来自截图。看不到或无法可靠确定的字段必须返回 null，严禁补全、推测或使用常识猜测。
            同一订单若同时包含消费和取餐码，应同时返回 expense 与 pickup；车票若同时包含票价与行程，应同时返回 expense 与 travel。
            不要把订单号、手机号、优惠金额或原价当作金额或取件码。无法可靠识别时 actions 返回空数组。
        """.trimIndent()
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
        if (imageDataUrl != null) {
            content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageDataUrl)))
        } else {
            content.put(JSONObject().put("type", "text").put("text", "本地 OCR 原文：\n${ocrText.orEmpty()}"))
        }
        return JSONArray().put(JSONObject().put("role", "user").put("content", content))
    }

    private fun connectionTestVisionMessages(): JSONArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val bytes = try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)) { "测试图片生成失败" }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        val dataUrl = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "这是一张连接测试图片，请只回复 OK"))
            .put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", dataUrl)
                )
            )
        return JSONArray().put(JSONObject().put("role", "user").put("content", content))
    }

    private fun friendlyHttpError(code: Int, serverMessage: String): String {
        val reason = when (code) {
            400, 422 -> "请求格式、模型名称或当前模型能力不匹配"
            401 -> "API Key 无效，或 Key 与 API 地址不属于同一服务/地域"
            403 -> "当前 Key 没有该模型权限，或服务尚未开通"
            404 -> "API 地址或模型名称不存在"
            408, 504 -> "服务响应超时"
            429 -> "请求过于频繁、余额不足或额度已用完"
            in 500..599 -> "服务商暂时不可用"
            else -> "服务返回异常"
        }
        val detail = serverMessage.trim().takeIf(String::isNotBlank)?.take(220)
        return "连接失败（HTTP $code）：$reason${detail?.let { "\n服务商信息：$it" }.orEmpty()}"
    }

    private fun friendlyConnectionError(error: Throwable): String = when (error) {
        is AiHttpException -> error.message ?: "AI 服务返回异常"
        is UnknownHostException -> "连接失败：无法解析 API 地址，请检查域名和手机网络"
        is SocketTimeoutException -> "连接失败：服务响应超时，请稍后重试"
        is ConnectException -> "连接失败：无法连接服务器，请检查 API 地址和网络"
        is SSLException -> "连接失败：HTTPS 证书或 TLS 握手异常"
        else -> error.message?.takeIf(String::isNotBlank) ?: "连接失败：未知错误"
    }

    private fun requireCloudConfig(config: AiReceiptConfig) {
        require(config.baseUrl.isNotBlank()) { "请填写 API 地址" }
        require(config.apiKey.isNotBlank()) { "请填写 API Key" }
        require(config.model.isNotBlank()) { "请填写模型名称" }
    }

    private fun encodeJpeg(context: Context, uri: Uri): ByteArray {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val scale = minOf(1f, MAX_IMAGE_DIMENSION.toFloat() / maxOf(width, height))
                if (scale < 1f) decoder.setTargetSize((width * scale).toInt(), (height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_DIMENSION) sampleSize *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("无法读取所选图片")
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "图片压缩失败" }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private class AiHttpException(val code: Int, message: String) : IllegalStateException(message)

    private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    private const val MAX_IMAGE_DIMENSION = 2560
    private const val SCREEN_REQUEST_TIMEOUT_MILLIS = 55_000L
}
