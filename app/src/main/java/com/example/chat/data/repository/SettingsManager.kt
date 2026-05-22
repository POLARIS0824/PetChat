package com.example.chat.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.chat.BuildConfig
import com.example.chat.R
import com.example.chat.model.UserProfile
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

    fun getUserProfile(): UserProfile {
        val username = prefs.getString(KEY_USER_NAME, "Mrh Raju") ?: "Mrh Raju"
        val signature = prefs.getString(KEY_USER_SIGNATURE, "在云朵里养宠物，是生活的小惊喜") ?: "在云朵里养宠物，是生活的小惊喜"
        val avatar = prefs.getInt(KEY_USER_AVATAR, R.drawable.avatar1)
        return UserProfile(username, signature, avatar)
    }

    fun saveUserProfile(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_USER_NAME, profile.username.trim())
            .putString(KEY_USER_SIGNATURE, profile.signature.trim())
            .putInt(KEY_USER_AVATAR, profile.avatarResId)
            .apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_SIGNATURE = "user_signature"
        private const val KEY_USER_AVATAR = "user_avatar"
    }
}

