package com.jianzhang.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiReceiptSettingsTest {
    private val qwen = AiServiceProfile(
        id = "qwen-1",
        name = "我的千问",
        provider = AiProvider.QWEN,
        baseUrl = "https://qwen.example.com/v1",
        apiKey = "fake-qwen-key",
        model = "qwen-vl"
    )
    private val deepSeek = AiServiceProfile(
        id = "deepseek-1",
        name = "我的 DeepSeek",
        provider = AiProvider.DEEPSEEK,
        baseUrl = "https://deepseek.example.com/v1",
        apiKey = "fake-deepseek-key",
        model = "deepseek-vision"
    )

    @Test
    fun switchesActiveProfileWithoutLosingOtherCredentials() {
        val settings = AiReceiptSettings(
            enabled = true,
            mode = AiRecognitionMode.VISION,
            activeProfileId = qwen.id,
            profiles = listOf(qwen, deepSeek)
        )

        assertEquals("fake-qwen-key", settings.activeConfig?.apiKey)
        val switched = settings.copy(activeProfileId = deepSeek.id)
        assertEquals("fake-deepseek-key", switched.activeConfig?.apiKey)
        assertEquals(listOf("fake-qwen-key", "fake-deepseek-key"), switched.profiles.map { it.apiKey })
    }

    @Test
    fun cloudConfigIsUnavailableWhenDisabledOrLocal() {
        val settings = AiReceiptSettings(
            enabled = false,
            mode = AiRecognitionMode.VISION,
            activeProfileId = qwen.id,
            profiles = listOf(qwen)
        )

        assertNull(settings.activeConfig)
        assertNull(settings.copy(enabled = true, mode = AiRecognitionMode.LOCAL).activeConfig)
    }
}
