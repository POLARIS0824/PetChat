package com.example.chat.model

data class ChatSession(
    val id: String,
    val petType: PetTypes,
    val displayName: String,
    val avatarRes: Int, // 头像资源ID
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0
) 