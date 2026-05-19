package com.example.chat.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.chat.data.dao.ChatDao
import com.example.chat.model.PetTypes
import com.example.chat.model.SessionInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context,
    private val chatDao: ChatDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "petchat_session", Context.MODE_PRIVATE
    )

    var currentSessionId: String = prefs.getString(KEY_SESSION_ID, null)
        ?: UUID.randomUUID().toString().also { saveSessionId(it) }
        private set

    fun createNewSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        saveSessionId(currentSessionId)
        return currentSessionId
    }

    fun setCurrentSessionId(sessionId: String) {
        currentSessionId = sessionId
        saveSessionId(sessionId)
    }

    private fun saveSessionId(sessionId: String) {
        prefs.edit().putString(KEY_SESSION_ID, sessionId).apply()
    }

    suspend fun getSessionMessages(sessionId: String, petType: PetTypes): List<com.example.chat.data.entity.ChatEntity> =
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

    companion object {
        private const val KEY_SESSION_ID = "current_session_id"
    }
}
