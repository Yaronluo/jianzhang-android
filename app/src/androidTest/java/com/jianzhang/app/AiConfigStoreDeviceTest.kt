package com.jianzhang.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jianzhang.app.data.preferences.AiConfigStore
import com.jianzhang.app.domain.ai.AiProvider
import com.jianzhang.app.domain.ai.AiReceiptSettings
import com.jianzhang.app.domain.ai.AiRecognitionMode
import com.jianzhang.app.domain.ai.AiServiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiConfigStoreDeviceTest {
    @Test
    fun persistsMultipleProfilesAndEncryptsEveryApiKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesName = "ai_receipt_settings_device_test"
        val store = AiConfigStore(
            context = context,
            preferencesName = preferencesName,
            keyAlias = "jianzhang_ai_api_key_device_test"
        )
        val firstKey = "device-test-key-one"
        val secondKey = "device-test-key-two"
        val profiles = listOf(
            AiServiceProfile(
                id = "qwen-test",
                name = "测试千问",
                provider = AiProvider.QWEN,
                baseUrl = AiProvider.QWEN.defaultBaseUrl,
                apiKey = firstKey,
                model = AiProvider.QWEN.defaultModel
            ),
            AiServiceProfile(
                id = "deepseek-test",
                name = "测试 DeepSeek",
                provider = AiProvider.DEEPSEEK,
                baseUrl = AiProvider.DEEPSEEK.defaultBaseUrl,
                apiKey = secondKey,
                model = AiProvider.DEEPSEEK.defaultModel
            )
        )

        try {
            store.clear()
            store.save(
                AiReceiptSettings(
                    enabled = true,
                    mode = AiRecognitionMode.VISION,
                    activeProfileId = profiles[1].id,
                    profiles = profiles
                )
            )

            val loaded = store.load()
            assertEquals(2, loaded.profiles.size)
            assertEquals(profiles[1].id, loaded.activeProfileId)
            assertEquals(listOf(firstKey, secondKey), loaded.profiles.map { it.apiKey })

            val raw = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString("profiles_json", "").orEmpty()
            assertFalse(raw.contains(firstKey))
            assertFalse(raw.contains(secondKey))
            assertEquals(2, "api_key_encrypted".toRegex().findAll(raw).count())
            assertTrue(raw.contains("api_key_iv"))
        } finally {
            store.clear()
        }
    }
}
