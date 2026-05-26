package com.example.chat.data.repository

import android.util.Log
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatEntity
import com.example.chat.data.tools.ToolRegistry
import com.example.chat.model.ApiConfig
import com.example.chat.model.ChatMessage
import com.example.chat.model.DeepseekResponse
import com.example.chat.model.Message
import com.example.chat.model.PetType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

class ChatRepositoryTest {

    private val chatDao: ChatDao = mock()
    private val apiService: ChatApiService = mock()
    private val sessionManager: SessionManager = mock()
    private val promptBuilder: PromptBuilder = mock()
    private val pictureInfoParser: PictureInfoParser = mock()
    private val analysisUseCase: ChatAnalysisUseCase = mock()
    private val settingsManager: SettingsManager = mock()
    private val toolRegistry: ToolRegistry = mock()

    private lateinit var repository: ChatRepository
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setUp() {
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.e(any(), any(), any()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(any(), any()) }.thenReturn(0)

        // Stub default currentSessionId
        whenever(sessionManager.currentSessionId).thenReturn("test-session-id")
        
        // Stub default API configuration
        runBlocking {
            whenever(settingsManager.getConfig()).thenReturn(
                ApiConfig(
                    baseUrl = "https://example.com",
                    apiKey = "api-key",
                    model = "deepseek-v3"
                )
            )
        }

        repository = ChatRepository(
            chatDao = chatDao,
            apiService = apiService,
            sessionManager = sessionManager,
            promptBuilder = promptBuilder,
            pictureInfoParser = pictureInfoParser,
            analysisUseCase = analysisUseCase,
            settingsManager = settingsManager,
            toolRegistry = toolRegistry
        )
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    // region Chat Save & Importance Eval Tests

    @Test
    fun testSaveChatMessage_normalMessageNotImportant() = runTest {
        val chatMessage = ChatMessage(content = "吃了吗", role = "user", petType = PetType.CAT)
        
        // 未处理数设置为 5（不足 20 条自动总结阈值）
        whenever(chatDao.getUnprocessedChatsCount()).thenReturn(5)

        repository.saveChatMessage(chatMessage, PetType.CAT)

        // 验证插入数据库，且 isImportant 为 false
        argumentCaptor<ChatEntity>().apply {
            verify(chatDao).insert(capture())
            assertEquals("吃了吗", firstValue.content)
            assertEquals("test-session-id", firstValue.sessionId)
            assertEquals(PetType.CAT.name, firstValue.petType)
            assertEquals("user", firstValue.role)
            assertFalse(firstValue.isImportant)
        }

        // 验证不会调用自动总结
        verify(analysisUseCase, never()).summarizeConversation()
    }

    @Test
    fun testSaveChatMessage_importantBecauseOfQuestion() = runTest {
        val chatMessage = ChatMessage(content = "你喜欢吃什么糖?", role = "user", petType = PetType.CAT)
        whenever(chatDao.getUnprocessedChatsCount()).thenReturn(5)

        repository.saveChatMessage(chatMessage, PetType.CAT)

        argumentCaptor<ChatEntity>().apply {
            verify(chatDao).insert(capture())
            assertTrue(firstValue.isImportant) // 包含 "?" 应当是重要消息
        }
    }

    @Test
    fun testSaveChatMessage_importantBecauseOfKeyword() = runTest {
        val chatMessage = ChatMessage(content = "我想要一个毛绒玩具", role = "user", petType = PetType.CAT)
        whenever(chatDao.getUnprocessedChatsCount()).thenReturn(5)

        repository.saveChatMessage(chatMessage, PetType.CAT)

        argumentCaptor<ChatEntity>().apply {
            verify(chatDao).insert(capture())
            assertTrue(firstValue.isImportant) // 包含 "想要" 应当是重要消息
        }
    }

    @Test
    fun testSaveChatMessage_importantBecauseOfLength() = runTest {
        val longContent = "这是一个非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常特别特别特别特别长的消息！"
        val chatMessage = ChatMessage(content = longContent, role = "user", petType = PetType.CAT)
        whenever(chatDao.getUnprocessedChatsCount()).thenReturn(5)

        repository.saveChatMessage(chatMessage, PetType.CAT)

        argumentCaptor<ChatEntity>().apply {
            verify(chatDao).insert(capture())
            assertTrue(firstValue.isImportant) // 长度大于 50 字符应当是重要消息
        }
    }

    @Test
    fun testSaveChatMessage_triggerSummaryWhenThresholdExceeded() = runTest {
        val chatMessage = ChatMessage(content = "你好", role = "user", petType = PetType.CAT)
        
        // 模拟未处理聊天记录数达到 21 条（超出 SUMMARY_THRESHOLD 20）
        whenever(chatDao.getUnprocessedChatsCount()).thenReturn(21)

        repository.saveChatMessage(chatMessage, PetType.CAT)

        // 验证在插入后触发了自动对话总结
        verify(analysisUseCase).summarizeConversation()
    }

    @Test
    fun testSaveChatMessage_noSummaryWhenThresholdNotExceeded() = runTest {
        val chatMessage = ChatMessage(content = "你好", role = "user", petType = PetType.CAT)
        
        // 模拟未处理聊天记录数刚好是 20 条（等于 SUMMARY_THRESHOLD 20，不触发）
        whenever(chatDao.getUnprocessedChatsCount()).thenReturn(20)

        repository.saveChatMessage(chatMessage, PetType.CAT)

        // 验证没有触发自动总结
        verify(analysisUseCase, never()).summarizeConversation()
    }

    // endregion

    // region Context Build & API Fallback Tests

    @Test
    fun testGetPetResponse_buildsContextProperly() = runTest {
        val petType = PetType.CAT
        val userMsgContent = "来玩吧"
        
        // 模拟已有的最近 3 条聊天历史记录（CONTEXT_MESSAGE_LIMIT）
        val recentHistory = listOf(
            ChatEntity(id = 1L, content = "哈？", role = "assistant", petType = petType.name, sessionId = "session", timestamp = 100L),
            ChatEntity(id = 2L, content = "给你罐头", role = "user", petType = petType.name, sessionId = "session", timestamp = 200L)
        )
        whenever(chatDao.getRecentSessionMessages(any(), any(), eq(3))).thenReturn(recentHistory)
        whenever(promptBuilder.build(petType)).thenReturn("猫咪傲娇设定")

        // Stub API 响应
        val apiResponse = DeepseekResponse(
            choices = listOf(
                DeepseekResponse.Choice(
                    message = Message(role = "assistant", content = "哼，本喵才不想和你玩！")
                )
            )
        )
        whenever(apiService.makeApiRequest(any())).thenReturn(apiResponse)

        val reply = repository.getPetResponse(petType, userMsgContent)

        assertEquals("哼，本喵才不想和你玩！", reply)

        // 验证发送给 API 的上下文消息是否组织正确：
        // 1. 系统角色设定（index 0）
        // 2. 聊天历史（按时间戳排序）（index 1, 2）
        // 3. 用户当前的最新提问（index 3）
        argumentCaptor<com.example.chat.model.DeepseekRequest>().apply {
            verify(apiService).makeApiRequest(capture())
            val messages = firstValue.messages
            assertEquals(4, messages.size)
            assertEquals("system", messages[0].role)
            assertEquals("猫咪傲娇设定", messages[0].content)
            
            assertEquals("assistant", messages[1].role)
            assertEquals("哈？", messages[1].content)
            
            assertEquals("user", messages[2].role)
            assertEquals("给你罐头", messages[2].content)
            
            assertEquals("user", messages[3].role)
            assertEquals("来玩吧", messages[3].content)
        }
    }

    @Test
    fun testGetPetResponse_apiFailureGracefulFallback() = runTest {
        val petType = PetType.CAT
        whenever(chatDao.getRecentSessionMessages(any(), any(), any())).thenReturn(emptyList())
        whenever(promptBuilder.build(petType)).thenReturn("设定")

        // 模拟 API 请求发生网络或服务器异常
        whenever(apiService.makeApiRequest(any())).thenThrow(RuntimeException("网络故障"))

        val reply = repository.getPetResponse(petType, "你好")

        // 验证不会因为异常导致应用崩溃，而是优雅地返回了默认的系统安慰兜底语
        assertEquals("抱歉，我现在有点累了，待会再聊吧。", reply)
    }

    // endregion
}
