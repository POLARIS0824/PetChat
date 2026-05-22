package com.example.chat.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.SharedPreferencesMigration

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
