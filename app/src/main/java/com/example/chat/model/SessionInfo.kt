package com.example.chat.model

data class SessionInfo(
    val sessionId: String,
    val petType: PetTypes,
    val petName: String,
    val lastMessage: String,
    val timestamp: Long
)
