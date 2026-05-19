package com.example.chat.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index("petType")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val petType: String,  // 用于区分是猫咪还是狗狗的便利贴
    val timestamp: Long = System.currentTimeMillis()
)