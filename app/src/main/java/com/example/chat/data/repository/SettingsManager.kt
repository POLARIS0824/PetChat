package com.example.chat.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.chat.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("petchat_api", Context.MODE_PRIVATE)

    fun getConfig(): ApiConfig = ApiConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.PETCHAT_BASE_URL.trim().trimEnd('/'),
        apiKey = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.PETCHAT_API_KEY.trim(),
        model = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.PETCHAT_MODEL.trim().takeIf { it.isNotBlank() } ?: "deepseek-v3",
    )

    fun getCustomBaseUrl(): String = prefs.getString(KEY_BASE_URL, "") ?: ""
    fun getCustomModel(): String = prefs.getString(KEY_MODEL, "") ?: ""

    fun saveConfig(config: ApiConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim().trimEnd('/'))
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.model.trim().takeIf { it.isNotBlank() } ?: "")
            .apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
    }
}
