package com.jianzhang.app.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.jianzhang.app.domain.ai.AiProvider
import com.jianzhang.app.domain.ai.AiReceiptSettings
import com.jianzhang.app.domain.ai.AiRecognitionMode
import com.jianzhang.app.domain.ai.AiServiceProfile
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores multiple user-provided AI profiles; every API key is encrypted by Android Keystore. */
class AiConfigStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
    private val keyAlias: String = KEY_ALIAS
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun load(): AiReceiptSettings {
        val profiles = loadProfiles().ifEmpty { loadLegacyProfile()?.let(::listOf).orEmpty() }
        val requestedActiveId = preferences.getString(KEY_ACTIVE_PROFILE_ID, null)
        return AiReceiptSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            mode = AiRecognitionMode.fromId(
                preferences.getString(KEY_MODE, AiRecognitionMode.VISION.id).orEmpty()
            ),
            activeProfileId = requestedActiveId?.takeIf { id -> profiles.any { it.id == id } }
                ?: profiles.firstOrNull()?.id,
            profiles = profiles
        )
    }

    fun save(settings: AiReceiptSettings) {
        val profilesJson = JSONArray().apply {
            settings.profiles.forEach { profile ->
                val (ciphertext, iv) = encrypt(profile.apiKey.trim())
                put(
                    JSONObject()
                        .put("id", profile.id)
                        .put("name", profile.name.trim())
                        .put("provider", profile.provider.id)
                        .put("base_url", profile.baseUrl.trim().trimEnd('/'))
                        .put("model", profile.model.trim())
                        .put("api_key_encrypted", ciphertext)
                        .put("api_key_iv", iv)
                )
            }
        }
        val activeId = settings.activeProfileId?.takeIf { id -> settings.profiles.any { it.id == id } }
        check(
            preferences.edit()
                .putInt(KEY_VERSION, 2)
                .putBoolean(KEY_ENABLED, settings.enabled)
                .putString(KEY_MODE, settings.mode.id)
                .putString(KEY_ACTIVE_PROFILE_ID, activeId)
                .putString(KEY_PROFILES_JSON, profilesJson.toString())
                .remove(KEY_LEGACY_PROVIDER)
                .remove(KEY_LEGACY_BASE_URL)
                .remove(KEY_LEGACY_MODEL)
                .remove(KEY_LEGACY_API_KEY)
                .remove(KEY_LEGACY_API_KEY_IV)
                .commit()
        ) { "AI 配置保存失败" }
    }

    fun clear() {
        preferences.edit().clear().apply()
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        }
    }

    private fun loadProfiles(): List<AiServiceProfile> {
        val source = preferences.getString(KEY_PROFILES_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(source)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: return@repeat
                    val provider = AiProvider.fromId(item.optString("provider"))
                    add(
                        AiServiceProfile(
                            id = id,
                            name = item.optString("name", provider.displayName),
                            provider = provider,
                            baseUrl = item.optString("base_url", provider.defaultBaseUrl),
                            apiKey = decrypt(
                                item.optString("api_key_encrypted"),
                                item.optString("api_key_iv")
                            ),
                            model = item.optString("model", provider.defaultModel)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Reads the single-profile format used by the first BYOK implementation. */
    private fun loadLegacyProfile(): AiServiceProfile? {
        val encrypted = preferences.getString(KEY_LEGACY_API_KEY, null) ?: return null
        val iv = preferences.getString(KEY_LEGACY_API_KEY_IV, null) ?: return null
        val provider = AiProvider.fromId(preferences.getString(KEY_LEGACY_PROVIDER, AiProvider.QWEN.id).orEmpty())
        return AiServiceProfile(
            id = "legacy-${provider.id}",
            name = provider.displayName,
            provider = provider,
            baseUrl = preferences.getString(KEY_LEGACY_BASE_URL, provider.defaultBaseUrl).orEmpty(),
            apiKey = decrypt(encrypted, iv),
            model = preferences.getString(KEY_LEGACY_MODEL, provider.defaultModel).orEmpty()
        )
    }

    private fun encrypt(plainText: String): Pair<String, String> {
        if (plainText.isBlank()) return "" to ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(encrypted: String, iv: String): String {
        if (encrypted.isBlank() || iv.isBlank()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "ai_receipt_settings"
        const val KEY_VERSION = "settings_version"
        const val KEY_ENABLED = "enabled"
        const val KEY_MODE = "mode"
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        const val KEY_PROFILES_JSON = "profiles_json"
        const val KEY_LEGACY_PROVIDER = "provider"
        const val KEY_LEGACY_BASE_URL = "base_url"
        const val KEY_LEGACY_MODEL = "model"
        const val KEY_LEGACY_API_KEY = "api_key_encrypted"
        const val KEY_LEGACY_API_KEY_IV = "api_key_iv"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "jianzhang_ai_api_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
