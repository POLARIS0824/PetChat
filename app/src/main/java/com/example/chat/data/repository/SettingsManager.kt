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
        baseUrl = prefs.getString(KEY_BASE_URL, null)
            ?: BuildConfig.PETCHAT_BASE_URL.trim().trimEnd('/'),
        apiKey = prefs.getString(KEY_API_KEY, null)
            ?: BuildConfig.PETCHAT_API_KEY.trim(),
        model = prefs.getString(KEY_MODEL, null)
            ?: BuildConfig.PETCHAT_MODEL.trim().ifBlank { "deepseek-v3" },
    )

    fun saveConfig(config: ApiConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim().trimEnd('/'))
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.model.trim().ifBlank { "deepseek-v3" })
            .apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
    }
}
