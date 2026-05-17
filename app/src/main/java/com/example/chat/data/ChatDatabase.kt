package com.example.chat.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatEntity::class, ChatAnalysisEntity::class, NoteEntity::class],
    version = 4,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
