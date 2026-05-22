package com.example.chat.data.repository

import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatEntity
import com.example.chat.model.PetType
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*

class SessionManagerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val chatDao: ChatDao = mock()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionManager: SessionManager

    private val KEY_SESSION_ID = stringPreferencesKey("current_session_id")

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_session.preferences_pb") }
        )
    }

    @Test
    fun testInitialization_existingSessionId() = runTest {
        // 模拟已存在 sessionId 的情况
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_ID] = "existing-session-123"
        }

        sessionManager = SessionManager(dataStore, chatDao)

        assertEquals("existing-session-123", sessionManager.currentSessionId)
    }

    @Test
    fun testInitialization_noExistingSessionId() = runTest {
        // 模拟没有已存在 sessionId 的情况，初始化时应生成一个新的 UUID 并保存
        sessionManager = SessionManager(dataStore, chatDao)

        val id = sessionManager.currentSessionId
        assertNotNull(id)
        assertTrue(id.isNotEmpty())

        val savedId = dataStore.data.first()[KEY_SESSION_ID]
        assertEquals(id, savedId)
    }

    @Test
    fun testCreateNewSession() = runTest {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_ID] = "old-session-id"
        }

        sessionManager = SessionManager(dataStore, chatDao)
        assertEquals("old-session-id", sessionManager.currentSessionId)

        val newSessionId = sessionManager.createNewSession()

        assertNotEquals("old-session-id", newSessionId)
        assertEquals(newSessionId, sessionManager.currentSessionId)

        val savedId = dataStore.data.first()[KEY_SESSION_ID]
        assertEquals(newSessionId, savedId)
    }

    @Test
    fun testSetCurrentSessionId() = runTest {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_ID] = "some-session"
        }

        sessionManager = SessionManager(dataStore, chatDao)
        sessionManager.setCurrentSessionId("custom-session-999")

        assertEquals("custom-session-999", sessionManager.currentSessionId)

        val savedId = dataStore.data.first()[KEY_SESSION_ID]
        assertEquals("custom-session-999", savedId)
    }

    @Test
    fun testGetSessionMessages() = runTest {
        sessionManager = SessionManager(dataStore, chatDao)

        val dummyHistory = listOf(
            ChatEntity(id = 1, content = "嗨", role = "user", petType = PetType.CAT.name, sessionId = "session-abc")
        )
        whenever(chatDao.getSessionMessages("session-abc", "CAT")).thenReturn(dummyHistory)

        val messages = sessionManager.getSessionMessages("session-abc", PetType.CAT)

        assertEquals(1, messages.size)
        assertEquals("嗨", messages[0].content)
        verify(chatDao).getSessionMessages("session-abc", "CAT")
    }

    @Test
    fun testGetAllSessions() = runTest {
        sessionManager = SessionManager(dataStore, chatDao)

        val dummySessions = listOf(
            ChatDao.SessionEntity(
                sessionId = "session-1",
                petType = "CAT",
                lastMessage = "喵",
                timestamp = 1000L
            ),
            ChatDao.SessionEntity(
                sessionId = "session-2",
                petType = "DOG",
                lastMessage = "汪",
                timestamp = 2000L
            )
        )
        whenever(chatDao.getAllSessions()).thenReturn(dummySessions)

        val sessions = sessionManager.getAllSessions()

        assertEquals(2, sessions.size)
        assertEquals("session-1", sessions[0].sessionId)
        assertEquals(PetType.CAT, sessions[0].petType)
        assertEquals("喵", sessions[0].lastMessage)

        assertEquals("session-2", sessions[1].sessionId)
        assertEquals(PetType.DOG, sessions[1].petType)
        assertEquals("汪", sessions[1].lastMessage)

        verify(chatDao).getAllSessions()
    }
}
