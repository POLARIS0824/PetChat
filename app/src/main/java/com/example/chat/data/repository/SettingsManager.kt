package com.example.chat.data.repository

import com.example.chat.R
import com.example.chat.model.UserProfile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val configFlow: Flow<ApiConfig> = dataStore.data.map { prefs ->
        ApiConfig(
            baseUrl = prefs[KEY_BASE_URL]?.takeIf { it.isNotBlank() } ?: "",
            apiKey = prefs[KEY_API_KEY]?.takeIf { it.isNotBlank() } ?: "",
            model = prefs[KEY_MODEL]?.takeIf { it.isNotBlank() } ?: "",
        )
    }

    val userProfileFlow: Flow<UserProfile> = dataStore.data.map { prefs ->
        val username = prefs[KEY_USER_NAME] ?: "Mrh Raju"
        val signature = prefs[KEY_USER_SIGNATURE] ?: "在云朵里养宠物，是生活的小惊喜"
        val avatar = prefs[KEY_USER_AVATAR] ?: R.drawable.avatar1
        UserProfile(username, signature, avatar)
    }

    val customBaseUrlFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: ""
    }

    val customModelFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MODEL] ?: ""
    }

    suspend fun getConfig(): ApiConfig {
        return configFlow.first()
    }

    fun getConfigSync(): ApiConfig = runBlocking {
        getConfig()
    }

    suspend fun getCustomBaseUrl(): String {
        return customBaseUrlFlow.first()
    }

    suspend fun getCustomModel(): String {
        return customModelFlow.first()
    }

    suspend fun saveConfig(config: ApiConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = config.baseUrl.trim().trimEnd('/')
            prefs[KEY_API_KEY] = config.apiKey.trim()
            prefs[KEY_MODEL] = config.model.trim().takeIf { it.isNotBlank() } ?: ""
        }
    }

    suspend fun getUserProfile(): UserProfile {
        return userProfileFlow.first()
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = profile.username.trim()
            prefs[KEY_USER_SIGNATURE] = profile.signature.trim()
            prefs[KEY_USER_AVATAR] = profile.avatarResId
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.ppio.com/openai"
        const val DEFAULT_MODEL = "deepseek/deepseek-v4-flash"

        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_SIGNATURE = stringPreferencesKey("user_signature")
        private val KEY_USER_AVATAR = intPreferencesKey("user_avatar")
    }
}
