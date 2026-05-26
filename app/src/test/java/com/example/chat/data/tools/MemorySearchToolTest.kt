package com.example.chat.data.tools

import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatEntity
import com.example.chat.data.repository.SessionManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class MemorySearchToolTest {

    private val chatDao: ChatDao = mock()
    private val sessionManager: SessionManager = mock()
    private val memorySearchTool = MemorySearchTool(chatDao, sessionManager)

    @Test
    fun testSearchMemory_emptyResults() = runTest {
        whenever(sessionManager.currentSessionId).thenReturn("session-1")
        whenever(chatDao.searchMessagesByKeyword("session-1", "最喜欢的食物", 15))
            .thenReturn(emptyList())

        val result = memorySearchTool.execute("""{"query":"最喜欢的食物"}""")
        assertTrue(result.success)
        assertTrue(result.content.contains("没有找到"))
    }

    @Test
    fun testSearchMemory_findsRelevantMessages() = runTest {
        whenever(sessionManager.currentSessionId).thenReturn("session-1")
        val messages = listOf(
            ChatEntity(id = 1, content = "我喜欢吃鱼", role = "user", petType = "CAT",
                sessionId = "session-1", isImportant = true),
            ChatEntity(id = 2, content = "今天天气真好", role = "user", petType = "CAT",
                sessionId = "session-1"),
            ChatEntity(id = 3, content = "主人喜欢吃寿司吗？", role = "assistant", petType = "CAT",
                sessionId = "session-1")
        )
        whenever(chatDao.searchMessagesByKeyword("session-1", "吃", 15)).thenReturn(messages)

        val result = memorySearchTool.execute("""{"query":"吃"}""")
        assertTrue(result.success)
        assertTrue(result.content.contains("喜欢"))
    }

    @Test
    fun testSearchMemory_noRelevantResults() = runTest {
        whenever(sessionManager.currentSessionId).thenReturn("session-1")
        whenever(chatDao.searchMessagesByKeyword("session-1", "xyz不存在的关键词", 15))
            .thenReturn(emptyList())

        val result = memorySearchTool.execute("""{"query":"xyz不存在的关键词"}""")
        assertTrue(result.success)
    }
}
