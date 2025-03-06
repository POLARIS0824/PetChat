package com.example.chat.model

import java.util.Date

data class SocialPost(
    val id: String,
    val authorName: String,
    val authorAvatar: Int, // 资源ID
    val authorUsername: String,
    val content: String,
    val timestamp: Date,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false
)

data class Comment(
    val id: String,
    val authorName: String,
    val authorAvatar: Int, // 资源ID
    val authorUsername: String,
    val content: String,
    val timestamp: Date,
    val likeCount: Int = 0
)