package com.example.chat.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.chat.model.ApiConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsManagerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settingsManager: SettingsManager

    private val KEY_BASE_URL = stringPreferencesKey("base_url")

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_settings.preferences_pb") }
        )
        settingsManager = SettingsManager(dataStore)
    }

    @Test
    fun testGetCustomBaseUrl_defaultsToEmpty() = runTest {
        assertEquals("", settingsManager.getCustomBaseUrl())
    }

    @Test
    fun testGetCustomBaseUrl_returnsSavedValue() = runTest {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = "https://api.custom.com"
        }
        assertEquals("https://api.custom.com", settingsManager.getCustomBaseUrl())
    }

    @Test
    fun testGetConfig_defaultsToDefaultValuesWhenNotInitialized() = runTest {
        // 未配置过（config_initialized = false）时，应返回默认值
        val config = settingsManager.getConfig()
        assertEquals(SettingsManager.DEFAULT_BASE_URL, config.baseUrl)
        assertEquals(SettingsManager.DEFAULT_MODEL, config.model)
    }

    @Test
    fun testGetConfig_returnsEmptyWhenInitializedButBlank() = runTest {
        // 配置过但值为空白时，应返回空字符串
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = "   "
            prefs[booleanPreferencesKey("config_initialized")] = true
        }
        val configBlank = settingsManager.getConfig()
        assertEquals("", configBlank.baseUrl)
    }

    @Test
    fun testSaveConfig_andGetConfig() = runTest {
        val config = ApiConfig(
            baseUrl = "https://sync-api.example.com",
            apiKey = "sync-key",
            model = "custom-deepseek"
        )
        settingsManager.saveConfig(config)
        
        val loadedConfig = settingsManager.getConfig()
        assertEquals("https://sync-api.example.com", loadedConfig.baseUrl)
        assertEquals("sync-key", loadedConfig.apiKey)
        assertEquals("custom-deepseek", loadedConfig.model)
    }

    @Test
    fun testUserProfile_saveAndLoad() = runTest {
        val profile = com.example.chat.model.UserProfile(
            username = "Test User",
            signature = "My Signature",
            avatarResId = 999
        )
        settingsManager.saveUserProfile(profile)

        val loadedProfile = settingsManager.getUserProfile()
        assertEquals("Test User", loadedProfile.username)
        assertEquals("My Signature", loadedProfile.signature)
        assertEquals(999, loadedProfile.avatarResId)

        val flowProfile = settingsManager.userProfileFlow.first()
        assertEquals("Test User", flowProfile.username)
    }
}
