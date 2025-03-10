package com.example.chat.model

data class Pet(
    val name: String,
    val status: String,
    val imageRes: Int,
    val initalRes: Int,
    val finalRes: Int,
    val breed: String,     // 品种
    val age: String,       // 年龄
    val gender: String,     // 性别
    val character: String,
    val hobby: String,
    val petType: PetTypes
)
