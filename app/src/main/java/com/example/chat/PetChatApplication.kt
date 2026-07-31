package com.example.chat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.chat.data.repository.dataStore
import com.example.chat.service.PetGreetingWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit

@HiltAndroidApp
class PetChatApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            val alreadyScheduled = dataStore.data.first()[KEY_GREETING_SCHEDULED] ?: false
            if (!alreadyScheduled) {
                PetGreetingWorker.schedule(this@PetChatApplication, 9, 0)
                dataStore.edit { prefs -> prefs[KEY_GREETING_SCHEDULED] = true }
            }
        }
    }

    companion object {
        private val KEY_GREETING_SCHEDULED = booleanPreferencesKey("greeting_scheduled")
    }
}
