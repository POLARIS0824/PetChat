package com.example.chat.model

import androidx.annotation.DrawableRes

data class SocialPost(
    val id: String,
    val authorName: String,
    @DrawableRes val authorAvatar: Int,
    val authorUsername: String,
    val content: String,
    val timestamp: Long,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false
)

data class Comment(
    val id: String,
    val authorName: String,
    @DrawableRes val authorAvatar: Int,
    val authorUsername: String,
    val content: String,
    val timestamp: Long,
    val likeCount: Int = 0
)
