package com.example.chat.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.chat.model.ChatMessage
import com.example.chat.model.PetTypes

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val timestamp: Long,
    val isFromUser: Boolean,
    val petType: String  // 存储宠物类型的名称
) {
    fun toChatMessage(): ChatMessage {
        return ChatMessage(
            content = content,
            isFromUser = isFromUser,
            petType = PetTypes.valueOf(petType),
            timestamp = timestamp
        )
    }

    companion object {
        fun fromChatMessage(message: ChatMessage): MessageEntity {
            return MessageEntity(
                content = message.content,
                timestamp = message.timestamp,
                isFromUser = message.isFromUser,
                petType = message.petType.name
            )
        }
    }
}
