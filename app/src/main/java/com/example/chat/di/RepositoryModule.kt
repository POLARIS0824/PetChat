package com.example.chat.di

import com.example.chat.PetChatRepository
import com.example.chat.data.ChatDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun providePetChatRepository(chatDao: ChatDao): PetChatRepository {
        return PetChatRepository.getInstance(chatDao)
    }
}
