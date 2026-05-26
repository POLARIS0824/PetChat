package com.example.chat.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
    fun testGetConfig_defaultsToEmptyWhenNullOrBlank() = runTest {
        val config = settingsManager.getConfig()
        assertEquals("", config.baseUrl)
        assertEquals("", config.model)

        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = "   "
        }
        val configBlank = settingsManager.getConfig()
        assertEquals("", configBlank.baseUrl)
    }

    @Test
    fun testGetConfigSync_andSaveConfig() = runTest {
        val config = ApiConfig(
            baseUrl = "https://sync-api.example.com",
            apiKey = "sync-key",
            model = "custom-deepseek"
        )
        settingsManager.saveConfig(config)
        
        val loadedConfig = settingsManager.getConfigSync()
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
