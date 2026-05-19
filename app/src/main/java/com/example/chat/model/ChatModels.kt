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
    val content: String,    // 消息内容
    val isFromUser: Boolean, // 是否为用户消息
    val petType: PetType,  // 宠物类型
    val timestamp: Long = System.currentTimeMillis(), // 消息时间戳
    val role: String = if (isFromUser) "user" else "assistant", // 消息角色
)

/**
 * AI返回的图片相关信息数据类
 */
@Serializable
data class PictureInfo(
    val isPictureNeeded: Boolean,        // 是否需要配图
    val pictureDescription: String = ""   // 图片描述
) 