package com.jianzhang.app.domain.ai

import java.util.UUID

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    QWEN(
        id = "qwen",
        displayName = "阿里云千问",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen3-vl-plus"
    ),
    DOUBAO(
        id = "doubao",
        displayName = "火山方舟豆包",
        defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultModel = "doubao-seed-1-6-vision-250815"
    ),
    ZHIPU(
        id = "zhipu",
        displayName = "智谱 GLM",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-4.5v"
    ),
    HUNYUAN(
        id = "hunyuan",
        displayName = "腾讯混元",
        defaultBaseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
        defaultModel = "hunyuan-vision"
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-flash"
    ),
    CUSTOM(
        id = "custom",
        displayName = "自定义兼容接口",
        defaultBaseUrl = "",
        defaultModel = ""
    );

    companion object {
        fun fromId(id: String): AiProvider = entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}

enum class AiRecognitionMode(val id: String, val displayName: String) {
    VISION("vision", "AI 直接看图片"),
    OCR_TEXT("ocr_text", "本地 OCR 后交给 AI"),
    LOCAL("local", "完全本地识别");

    companion object {
        fun fromId(id: String): AiRecognitionMode = entries.firstOrNull { it.id == id } ?: LOCAL
    }
}

data class AiReceiptConfig(
    val enabled: Boolean = false,
    val provider: AiProvider = AiProvider.QWEN,
    val baseUrl: String = AiProvider.QWEN.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = AiProvider.QWEN.defaultModel,
    val mode: AiRecognitionMode = AiRecognitionMode.VISION
) {
    val isCloudReady: Boolean
        get() = enabled && mode != AiRecognitionMode.LOCAL &&
            baseUrl.trim().startsWith("https://") && apiKey.isNotBlank() && model.isNotBlank()
}

data class AiServiceProfile(
    val id: String,
    val name: String,
    val provider: AiProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    val isConfigured: Boolean
        get() = baseUrl.trim().startsWith("https://") && apiKey.isNotBlank() && model.isNotBlank()

    fun asConfig(mode: AiRecognitionMode): AiReceiptConfig = AiReceiptConfig(
        enabled = true,
        provider = provider,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        mode = mode
    )

    companion object {
        fun create(provider: AiProvider = AiProvider.QWEN): AiServiceProfile = AiServiceProfile(
            id = UUID.randomUUID().toString(),
            name = provider.displayName,
            provider = provider,
            baseUrl = provider.defaultBaseUrl,
            apiKey = "",
            model = provider.defaultModel
        )
    }
}

data class AiReceiptSettings(
    val enabled: Boolean = false,
    val mode: AiRecognitionMode = AiRecognitionMode.VISION,
    val activeProfileId: String? = null,
    val profiles: List<AiServiceProfile> = emptyList()
) {
    val activeProfile: AiServiceProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }

    val activeConfig: AiReceiptConfig?
        get() = activeProfile?.asConfig(mode)?.takeIf { enabled && it.isCloudReady }
}
