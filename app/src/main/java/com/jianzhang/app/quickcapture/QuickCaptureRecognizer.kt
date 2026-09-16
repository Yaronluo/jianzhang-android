package com.jianzhang.app.quickcapture

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.jianzhang.app.data.preferences.AiConfigStore
import com.jianzhang.app.domain.ai.AiReceiptClient
import com.jianzhang.app.domain.ai.AiRecognitionMode
import com.jianzhang.app.domain.ocr.LocalReceiptOcr
import com.jianzhang.app.domain.parser.ParsedReceipt
import com.jianzhang.app.domain.parser.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class QuickCaptureRecognizer(private val context: Context) {
    suspend fun recognize(bitmap: Bitmap, onState: (QuickCaptureState, String) -> Unit): ParsedReceipt {
        val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(directory, "quick_${UUID.randomUUID()}.jpg")
        try {
            withContext(Dispatchers.IO) {
                file.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "临时截图处理失败" }
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val settings = AiConfigStore(context).load()
            return when (settings.mode) {
                AiRecognitionMode.VISION -> {
                    val config = settings.activeConfig
                    if (config == null) {
                        localFallback(uri, null, onState, "AI 未配置，已回退OCR")
                    } else {
                        onState(QuickCaptureState.RECOGNIZING_AI, "AI正在识别账单……")
                        runCatching {
                            ReceiptParser.parseAiResponse(
                                AiReceiptClient.recognizeImage(context, uri, config),
                                "AI识别"
                            )
                        }.getOrElse { localFallback(uri, null, onState, "AI失败，已回退OCR") }
                    }
                }
                AiRecognitionMode.OCR_TEXT -> {
                    onState(QuickCaptureState.RECOGNIZING_OCR, "正在提取文字……")
                    val text = LocalReceiptOcr.recognize(context, uri)
                    require(text.isNotBlank()) { "没有识别到有效文字" }
                    val config = settings.activeConfig
                    if (config == null) {
                        localFallback(uri, text, onState, "AI 未配置，已回退OCR")
                    } else {
                        onState(QuickCaptureState.RECOGNIZING_AI, "AI正在分析账单……")
                        runCatching {
                            ReceiptParser.parseAiResponse(
                                AiReceiptClient.analyzeOcrText(config, text),
                                "AI+OCR识别"
                            ).copy(rawText = text)
                        }.getOrElse { localFallback(uri, text, onState, "AI失败，已回退OCR") }
                    }
                }
                AiRecognitionMode.LOCAL -> localFallback(uri, null, onState, "OCR识别")
            }
        } finally {
            withContext(Dispatchers.IO) { runCatching { file.delete() } }
        }
    }

    private suspend fun localFallback(
        uri: android.net.Uri,
        knownText: String?,
        onState: (QuickCaptureState, String) -> Unit,
        source: String
    ): ParsedReceipt {
        onState(QuickCaptureState.RECOGNIZING_OCR, "OCR正在识别……")
        val text = knownText ?: LocalReceiptOcr.recognize(context, uri)
        require(text.isNotBlank()) { "没有识别到有效文字" }
        onState(QuickCaptureState.PARSING, "正在整理账单……")
        return ReceiptParser.parse(text).copy(
            recognitionSource = source,
            warnings = if (source.contains("回退")) {
                listOf("AI 暂不可用，已使用本地 OCR 结果，请重点核对") + ReceiptParser.parse(text).warnings
            } else ReceiptParser.parse(text).warnings
        )
    }
}
