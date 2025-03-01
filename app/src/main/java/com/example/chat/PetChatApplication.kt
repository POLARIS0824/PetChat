package com.example.chat

import android.app.Application
import com.example.chat.data.ChatDatabase
import com.example.chat.service.PetGreetingWorker

/**
 * 自定义Application类
 * 用于在应用级别初始化和管理数据库实例
 */
class PetChatApplication : Application() {
    // 使用lazy委托确保数据库只在第一次使用时初始化
    val database: ChatDatabase by lazy { ChatDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        
        // 设置每天早上9点发送问候
        PetGreetingWorker.schedule(this, 9, 0)
    }
} 