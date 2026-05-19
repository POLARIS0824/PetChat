package com.example.chat.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.chat.data.dao.AnalysisDao
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.dao.NotesDao
import com.example.chat.data.entity.ChatAnalysisEntity
import com.example.chat.data.entity.ChatEntity
import com.example.chat.data.entity.NoteEntity

@Database(
    entities = [ChatEntity::class, ChatAnalysisEntity::class, NoteEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun notesDao(): NotesDao
}
