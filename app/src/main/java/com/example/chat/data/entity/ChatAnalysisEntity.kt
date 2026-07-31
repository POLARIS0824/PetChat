package com.example.chat.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.chat.model.PetType

@Entity(
    tableName = "chat_analysis",
    indices = [Index("petType")]
)
data class ChatAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val petType: PetType,
    val summary: String,
    val preferences: String,  // JSON 格式的用户偏好列表
    val patterns: String,     // JSON 格式的互动模式列表
    val timestamp: Long = System.currentTimeMillis()
)