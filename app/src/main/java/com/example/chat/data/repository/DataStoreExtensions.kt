package com.example.chat.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.SharedPreferencesMigration

/**
 * 全局 DataStore 实例。
 * 迁移的旧 SharedPreferences 文件名必须与历史版本完全一致：
 * - "petchat_session" → 包含 key "current_session_id"
 * - "petchat_api"     → 包含 keys "base_url", "api_key", "model"
 * - "pet_greeting"    → 包含 keys "hour", "minute", "pet_type"
 * 如历史版本使用不同名称/键名，需在此处同步修改，否则迁移将静默失效。
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "petchat_preferences",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "petchat_session"),
            SharedPreferencesMigration(context, "petchat_api"),
            SharedPreferencesMigration(context, "pet_greeting")
        )
    }
)
