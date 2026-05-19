package com.example.chat.model

import kotlinx.serialization.Serializable

/**
 * 宠物类型枚举
 * 定义支持的宠物类型及其显示名称
 */
enum class PetType(val displayName: String) {
    CAT("布丁"),
    DOG("大白"),
    HAMSTER("团绒"),
    SHIBA("豆豆"),
}

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val content: String,
    val role: String = "user",
    val petType: PetType,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * AI返回的图片相关信息数据类
 */
@Serializable
data class PictureInfo(
    val isPictureNeeded: Boolean,        // 是否需要配图
    val pictureDescription: String = ""   // 图片描述
) 