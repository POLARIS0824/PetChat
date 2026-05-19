package com.example.chat.data.repository

import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatEntity
import com.example.chat.model.PetTypes
import com.example.chat.model.SessionInfo
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val chatDao: ChatDao
) {
    @Volatile
    var currentSessionId: String = UUID.randomUUID().toString()
        private set

    fun createNewSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        return currentSessionId
    }

    fun setCurrentSessionId(sessionId: String) {
        currentSessionId = sessionId
    }

    suspend fun getSessionMessages(sessionId: String, petType: PetTypes): List<ChatEntity> =
        chatDao.getSessionMessages(sessionId, petType.name)

    suspend fun getAllSessions(): List<SessionInfo> {
        return chatDao.getAllSessions().map { entity ->
            val petType = PetTypes.entries.firstOrNull { it.name == entity.petType } ?: PetTypes.CAT
            SessionInfo(
                sessionId = entity.sessionId,
                petType = petType,
                petName = petType.displayName,
                lastMessage = entity.lastMessage,
                timestamp = entity.timestamp
            )
        }
    }
}
