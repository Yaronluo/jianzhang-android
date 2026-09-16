package com.jianzhang.app.quickcapture

import android.content.Context

class QuickCapturePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var defaultAccountId: String?
        get() = preferences.getString(KEY_DEFAULT_ACCOUNT, null)
        set(value) {
            preferences.edit().putString(KEY_DEFAULT_ACCOUNT, value).apply()
        }

    private companion object {
        const val NAME = "quick_capture_settings"
        const val KEY_DEFAULT_ACCOUNT = "default_account_id"
    }
}
