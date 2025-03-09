package com.example.chat.model

/**
 * 宠物类型枚举
 * 定义支持的宠物类型及其显示名称
 */
enum class PetTypes(val displayName: String) {
    CAT("布丁"),
    DOG("大白"),
    HAMSTER("团绒"),
    DOG2("豆豆"),
}

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val content: String,    // 消息内容
    val isFromUser: Boolean, // 是否为用户消息
    val petType: PetTypes,  // 宠物类型
    val timestamp: Long = System.currentTimeMillis() // 消息时间戳
)

/**
 * AI返回的图片相关信息数据类
 */
data class PictureInfo(
    val isPictureNeeded: Boolean,        // 是否需要配图
    val pictureDescription: String = ""   // 图片描述
) 