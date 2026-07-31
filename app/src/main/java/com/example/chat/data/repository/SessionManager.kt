package com.example.chat.data.repository

import com.example.chat.data.dao.ChatDao
import com.example.chat.model.PetType
import com.example.chat.model.SessionInfo
import com.example.chat.model.petTypeFromStorage
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val chatDao: ChatDao
) {
    private var _currentSessionId: String? = null

    suspend fun getCurrentSessionId(): String {
        _currentSessionId?.let { return it }
        val id = dataStore.data.first()[KEY_SESSION_ID]
            ?: UUID.randomUUID().toString().also { saveSessionId(it) }
        _currentSessionId = id
        return id
    }

    suspend fun createNewSession(): String {
        val newSession = UUID.randomUUID().toString()
        _currentSessionId = newSession
        saveSessionId(newSession)
        return newSession
    }

    suspend fun setCurrentSessionId(sessionId: String) {
        _currentSessionId = sessionId
        saveSessionId(sessionId)
    }

    private suspend fun saveSessionId(sessionId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SESSION_ID] = sessionId
        }
    }

    suspend fun getSessionMessages(sessionId: String, petType: PetType): List<com.example.chat.data.entity.ChatEntity> =
        chatDao.getSessionMessages(sessionId, petType)

    suspend fun getAllSessions(): List<SessionInfo> {
        return chatDao.getAllSessions().map { entity ->
            val petType = petTypeFromStorage(entity.petType)
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
        private val KEY_SESSION_ID = stringPreferencesKey("current_session_id")
    }
}
