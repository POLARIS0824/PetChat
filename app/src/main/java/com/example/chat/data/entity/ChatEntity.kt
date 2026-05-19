package com.example.chat.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_history",
    indices = [
        Index("petType"),
        Index("sessionId"),
        Index("isProcessed"),
        Index("sessionId", "petType"),
    ]
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val petType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false,
    val sessionId: String,
    val role: String = "user",
    val isImportant: Boolean = false,
    val isSummary: Boolean = false,
)
