package com.example.chat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 聊天记录的数据库实体类
 * 用于在 Room 数据库中存储聊天消息
 */
@Entity(tableName = "chat_history")
data class ChatEntity(
    // 自增主键ID
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 聊天消息的具体内容
    val content: String,
    
    // 标识消息是否来自用户（true：用户消息，false：宠物回复）
    val isFromUser: Boolean,
    
    // 当前选择的宠物类型（存储为字符串）
    val petType: String,
    
    // 消息的时间戳，默认为创建时的系统时间
    val timestamp: Long = System.currentTimeMillis(),
    
    // 标识该消息是否已经被AI处理过
    // 用于实现每10条消息进行一次分析的功能
    val isProcessed: Boolean = false
) 