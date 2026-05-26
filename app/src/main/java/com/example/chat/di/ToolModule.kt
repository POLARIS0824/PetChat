package com.example.chat.di

import com.example.chat.data.tools.MemorySearchTool
import com.example.chat.data.tools.NoteTool
import com.example.chat.data.tools.ReminderTool
import com.example.chat.data.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolModule {
    @Binds
    @IntoSet
    abstract fun bindNoteTool(noteTool: NoteTool): Tool

    @Binds
    @IntoSet
    abstract fun bindReminderTool(reminderTool: ReminderTool): Tool

    @Binds
    @IntoSet
    abstract fun bindMemorySearchTool(memorySearchTool: MemorySearchTool): Tool
}
