package com.example.chat.model

import androidx.annotation.DrawableRes

data class Pet(
    val name: String,
    val status: String,
    @DrawableRes val imageRes: Int,
    @DrawableRes val initialRes: Int,
    @DrawableRes val finalRes: Int,
    val breed: String,
    val age: String,
    val gender: String,
    val character: String,
    val hobby: String,
    val petType: PetType
)
