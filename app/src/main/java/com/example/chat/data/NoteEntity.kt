package com.example.chat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val petType: String,  // 用于区分是猫咪还是狗狗的便利贴
    val timestamp: Long = System.currentTimeMillis()
) 