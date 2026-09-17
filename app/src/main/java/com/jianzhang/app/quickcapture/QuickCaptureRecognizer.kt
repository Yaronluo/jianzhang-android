package com.jianzhang.app.quickcapture

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.jianzhang.app.data.preferences.AiConfigStore
import com.jianzhang.app.domain.ai.AiReceiptClient
import com.jianzhang.app.domain.ai.AiRecognitionMode
import com.jianzhang.app.domain.ocr.LocalReceiptOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

class QuickCaptureRecognizer(private val context: Context) {
    suspend fun recognize(bitmap: Bitmap, onState: (QuickCaptureState, String) -> Unit): ScreenAnalysis {
        val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(directory, "quick_${UUID.randomUUID()}.jpg")
        try {
            withContext(Dispatchers.IO) {
                file.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "临时截图处理失败" }
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            return recognize(uri, onState)
        } finally {
            withContext(Dispatchers.IO) { runCatching { file.delete() } }
        }
    }

    suspend fun recognize(uri: Uri, onState: (QuickCaptureState, String) -> Unit): ScreenAnalysis {
        val settings = AiConfigStore(context).load()
        return when (settings.mode) {
            AiRecognitionMode.VISION -> {
                val config = settings.activeConfig
                if (config == null) {
                    localFallback(uri, null, onState, "AI 未配置，已回退 OCR")
                } else {
                    onState(QuickCaptureState.RECOGNIZING_AI, "AI 正在理解图片内容……")
                    runCatching {
                        withTimeout(AI_ATTEMPT_TIMEOUT_MILLIS) {
                            ScreenAnalysisParser.parseAiResponse(
                                AiReceiptClient.analyzeScreenImage(context, uri, config),
                                "AI 图片识别"
                            )
                        }
                    }.getOrElse { localFallback(uri, null, onState, "AI 失败，已回退 OCR") }
                }
            }
            AiRecognitionMode.OCR_TEXT -> {
                onState(QuickCaptureState.RECOGNIZING_OCR, "正在提取图片文字……")
                val text = LocalReceiptOcr.recognize(context, uri)
                require(text.isNotBlank()) { "没有识别到有效文字" }
                val config = settings.activeConfig
                if (config == null) {
                    localFallback(uri, text, onState, "AI 未配置，已回退 OCR")
                } else {
                    onState(QuickCaptureState.RECOGNIZING_AI, "AI 正在判断可执行事项……")
                    runCatching {
                        withTimeout(AI_ATTEMPT_TIMEOUT_MILLIS) {
                            ScreenAnalysisParser.parseAiResponse(
                                AiReceiptClient.analyzeScreenText(config, text),
                                "AI + OCR 图片识别"
                            ).copy(rawText = text)
                        }
                    }.getOrElse { localFallback(uri, text, onState, "AI 失败，已回退 OCR") }
                }
            }
            AiRecognitionMode.LOCAL -> localFallback(uri, null, onState, "本地 OCR 识别")
        }
    }

    private suspend fun localFallback(
        uri: android.net.Uri,
        knownText: String?,
        onState: (QuickCaptureState, String) -> Unit,
        source: String
    ): ScreenAnalysis {
        onState(QuickCaptureState.RECOGNIZING_OCR, "OCR正在识别……")
        val text = knownText ?: LocalReceiptOcr.recognize(context, uri)
        require(text.isNotBlank()) { "没有识别到有效文字" }
        onState(QuickCaptureState.PARSING, "正在判断页面用途……")
        return LocalSceneParser.parse(text, source)
    }

    private companion object {
        // 为本地 OCR 回退预留足够时间，避免云端重试占满整个截图会话。
        const val AI_ATTEMPT_TIMEOUT_MILLIS = 70_000L
    }
}
