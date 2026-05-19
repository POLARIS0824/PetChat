package com.example.chat.di

import android.content.Context
import androidx.room.Room
import com.example.chat.data.dao.AnalysisDao
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.ChatDatabase
import com.example.chat.data.dao.NotesDao
import com.example.chat.data.repository.ChatApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChatDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            ChatDatabase::class.java,
            "chat_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChatDao(database: ChatDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideAnalysisDao(database: ChatDatabase): AnalysisDao {
        return database.analysisDao()
    }

    @Provides
    fun provideNotesDao(database: ChatDatabase): NotesDao {
        return database.notesDao()
    }

    @Provides
    @Singleton
    fun provideChatApiService(): ChatApiService {
        return ChatApiService()
    }
}
