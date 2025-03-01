package com.example.chat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_analysis")
data class ChatAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val petType: String,
    val summary: String,
    val preferences: String,  // JSON 格式的用户偏好列表
    val patterns: String,     // JSON 格式的互动模式列表
    val timestamp: Long = System.currentTimeMillis()
) 