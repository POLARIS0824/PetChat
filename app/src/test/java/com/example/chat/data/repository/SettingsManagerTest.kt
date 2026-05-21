package com.example.chat.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.chat.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SettingsManagerTest {

    private val context: Context = mock()
    private val sharedPreferences: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()

    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        whenever(context.getSharedPreferences(eq("petchat_api"), eq(Context.MODE_PRIVATE)))
            .thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)

        settingsManager = SettingsManager(context)
    }

    @Test
    fun testGetCustomBaseUrl_defaultsToEmpty() {
        whenever(sharedPreferences.getString(eq("base_url"), any())).thenReturn(null)
        assertEquals("", settingsManager.getCustomBaseUrl())
    }

    @Test
    fun testGetCustomBaseUrl_returnsSavedValue() {
        whenever(sharedPreferences.getString(eq("base_url"), any())).thenReturn("https://api.custom.com")
        assertEquals("https://api.custom.com", settingsManager.getCustomBaseUrl())
    }

    @Test
    fun testGetConfig_fallbackToBuildConfigWhenNullOrBlank() {
        whenever(sharedPreferences.getString(eq("base_url"), any())).thenReturn(null)
        whenever(sharedPreferences.getString(eq("api_key"), any())).thenReturn(null)
        whenever(sharedPreferences.getString(eq("model"), any())).thenReturn(null)

        val config = settingsManager.getConfig()
        assertEquals(BuildConfig.PETCHAT_BASE_URL.trim().trimEnd('/'), config.baseUrl)

        whenever(sharedPreferences.getString(eq("base_url"), any())).thenReturn("   ")
        val configBlank = settingsManager.getConfig()
        assertEquals(BuildConfig.PETCHAT_BASE_URL.trim().trimEnd('/'), configBlank.baseUrl)
    }
}
