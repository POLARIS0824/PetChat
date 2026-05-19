package com.example.chat.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.chat.data.dao.AnalysisDao
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.dao.NotesDao
import com.example.chat.data.entity.ChatAnalysisEntity
import com.example.chat.data.entity.ChatEntity
import com.example.chat.data.entity.NoteEntity

@Database(
    entities = [ChatEntity::class, ChatAnalysisEntity::class, NoteEntity::class],
    version = 8,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun notesDao(): NotesDao
}
