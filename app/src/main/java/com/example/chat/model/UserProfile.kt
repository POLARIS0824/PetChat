package com.example.chat.model

import androidx.annotation.DrawableRes

data class UserProfile(
    val username: String,
    val signature: String,
    @DrawableRes val avatarResId: Int
)
