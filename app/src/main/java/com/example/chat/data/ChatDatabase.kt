package com.example.chat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 数据库类
 * 使用单例模式确保整个应用只有一个数据库实例
 */
@Database(
    entities = [ChatEntity::class, ChatAnalysisEntity::class, NoteEntity::class],
    version = 4,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    // 提供对 DAO 的访问
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        /**
         * 获取数据库实例
         * 如果实例不存在则创建新实例，使用双重检查锁定确保线程安全
         */
        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                )
                .fallbackToDestructiveMigration()  // 如果数据库版本变化，重建数据库
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 