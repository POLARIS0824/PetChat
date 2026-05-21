package com.example.chat.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatEntity
import com.example.chat.model.PetType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class SessionManagerTest {

    private val context: Context = mock()
    private val sharedPrefs: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()
    private val chatDao: ChatDao = mock()

    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPrefs)
        whenever(sharedPrefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
    }

    @Test
    fun testInitialization_existingSessionId() {
        // 模拟已存在 sessionId 的情况
        whenever(sharedPrefs.getString(eq("current_session_id"), anyOrNull())).thenReturn("existing-session-123")

        sessionManager = SessionManager(context, chatDao)

        assertEquals("existing-session-123", sessionManager.currentSessionId)
        verify(editor, never()).putString(any(), any())
    }

    @Test
    fun testInitialization_noExistingSessionId() {
        // 模拟没有已存在 sessionId 的情况，初始化时应生成一个新的 UUID 并保存
        whenever(sharedPrefs.getString(eq("current_session_id"), anyOrNull())).thenReturn(null)

        sessionManager = SessionManager(context, chatDao)

        assertNotNull(sessionManager.currentSessionId)
        assertTrue(sessionManager.currentSessionId.isNotEmpty())
        verify(editor).putString(eq("current_session_id"), eq(sessionManager.currentSessionId))
        verify(editor).apply()
    }

    @Test
    fun testCreateNewSession() {
        whenever(sharedPrefs.getString(eq("current_session_id"), anyOrNull())).thenReturn("old-session-id")

        sessionManager = SessionManager(context, chatDao)
        assertEquals("old-session-id", sessionManager.currentSessionId)

        val newSessionId = sessionManager.createNewSession()

        assertNotEquals("old-session-id", newSessionId)
        assertEquals(newSessionId, sessionManager.currentSessionId)
        verify(editor).putString(eq("current_session_id"), eq(newSessionId))
        verify(editor).apply()
    }

    @Test
    fun testSetCurrentSessionId() {
        whenever(sharedPrefs.getString(eq("current_session_id"), anyOrNull())).thenReturn("some-session")

        sessionManager = SessionManager(context, chatDao)
        sessionManager.setCurrentSessionId("custom-session-999")

        assertEquals("custom-session-999", sessionManager.currentSessionId)
        verify(editor).putString(eq("current_session_id"), eq("custom-session-999"))
        verify(editor).apply()
    }

    @Test
    fun testGetSessionMessages() = runTest {
        whenever(sharedPrefs.getString(eq("current_session_id"), anyOrNull())).thenReturn("session-abc")
        sessionManager = SessionManager(context, chatDao)

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
        whenever(sharedPrefs.getString(eq("current_session_id"), anyOrNull())).thenReturn("session-abc")
        sessionManager = SessionManager(context, chatDao)

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
