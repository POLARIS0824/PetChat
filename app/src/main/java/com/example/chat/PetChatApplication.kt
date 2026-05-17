package com.example.chat

import android.app.Application
import com.example.chat.service.PetGreetingWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PetChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PetGreetingWorker.schedule(this, 9, 0)
    }
}
