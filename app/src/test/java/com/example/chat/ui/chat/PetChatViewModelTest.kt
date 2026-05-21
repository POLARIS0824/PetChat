package com.example.chat.ui.chat

import android.app.Application
import com.example.chat.R
import com.example.chat.data.entity.ChatEntity
import com.example.chat.data.repository.ChatRepository
import com.example.chat.data.repository.SessionManager
import com.example.chat.model.ChatMessage
import com.example.chat.model.ChatUiState
import com.example.chat.model.PetType
import com.example.chat.model.PictureInfo
import com.example.chat.model.SessionInfo
import com.example.chat.model.StreamResponseListener
import com.example.chat.ui.notes.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class PetChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: ChatRepository = mock()
    private val sessionManager: SessionManager = mock()
    private val application: Application = mock()

    private lateinit var viewModel: PetChatViewModel

    @Before
    fun setUp() {
        whenever(sessionManager.currentSessionId).thenReturn("test-session-id")
        whenever(application.getString(R.string.chat_error_fallback)).thenReturn("出错了喵，请稍后再试。")
    }

    private suspend fun initViewModel(history: List<ChatEntity> = emptyList()) {
        whenever(sessionManager.getSessionMessages(any(), any())).thenReturn(history)
        viewModel = PetChatViewModel(repository, sessionManager, application)
    }

    @Test
    fun testInitialization_loadsEmptyHistory() = runTest {
        initViewModel(emptyList())
        testScheduler.advanceUntilIdle()

        val state = viewModel.chatUiState.value
        assertTrue(state is ChatUiState.Ready)
        val readyState = state as ChatUiState.Ready
        assertTrue(readyState.chatHistory.isEmpty())
        assertEquals(PetType.CAT, readyState.currentPetType)
        assertFalse(readyState.isStreaming)
    }

    @Test
    fun testInitialization_loadsExistingHistory() = runTest {
        val history = listOf(
            ChatEntity(id = 1L, content = "你好", role = "user", petType = "CAT", sessionId = "test-session-id"),
            ChatEntity(id = 2L, content = "哼！本喵才不想理你！", role = "assistant", petType = "CAT", sessionId = "test-session-id")
        )
        initViewModel(history)
        testScheduler.advanceUntilIdle()

        val state = viewModel.chatUiState.value
        assertTrue(state is ChatUiState.Ready)
        val readyState = state as ChatUiState.Ready
        assertEquals(2, readyState.chatHistory.size)
        assertEquals("你好", readyState.chatHistory[0].content)
        assertEquals("user", readyState.chatHistory[0].role)
        assertEquals("哼！本喵才不想理你！", readyState.chatHistory[1].content)
        assertEquals("assistant", readyState.chatHistory[1].role)
    }

    @Test
    fun testSelectPetType_reloadsHistoryForNewPet() = runTest {
        val catHistory = listOf(
            ChatEntity(id = 1L, content = "猫咪对话", role = "user", petType = "CAT", sessionId = "test-session-id")
        )
        whenever(sessionManager.getSessionMessages("test-session-id", PetType.CAT)).thenReturn(catHistory)
        viewModel = PetChatViewModel(repository, sessionManager, application)
        testScheduler.advanceUntilIdle()

        // 切换为 DOG 且模拟 DOG 包含 2 条消息
        val dogHistory = listOf(
            ChatEntity(id = 2L, content = "狗狗消息1", role = "user", petType = "DOG", sessionId = "test-session-id"),
            ChatEntity(id = 3L, content = "狗狗消息2", role = "assistant", petType = "DOG", sessionId = "test-session-id")
        )
        whenever(sessionManager.getSessionMessages("test-session-id", PetType.DOG)).thenReturn(dogHistory)

        viewModel.selectPetType(PetType.DOG)
        testScheduler.advanceUntilIdle()

        val state = viewModel.chatUiState.value as ChatUiState.Ready
        assertEquals(PetType.DOG, state.currentPetType)
        assertEquals(2, state.chatHistory.size)
        assertEquals("狗狗消息1", state.chatHistory[0].content)
    }

    @Test
    fun testCreateNewSession_clearsHistoryAndLoadsSessions() = runTest {
        initViewModel(emptyList())
        testScheduler.advanceUntilIdle()

        val mockSessions = listOf(
            SessionInfo("session-1", PetType.CAT, "布丁", "最近聊天", 123456L)
        )
        whenever(sessionManager.getAllSessions()).thenReturn(mockSessions)

        viewModel.createNewSession()
        testScheduler.advanceUntilIdle()

        // 验证调用了 sessionManager 的创建新会话
        verify(sessionManager).createNewSession()
        
        // 验证聊天历史已清空
        val state = viewModel.chatUiState.value as ChatUiState.Ready
        assertTrue(state.chatHistory.isEmpty())

        // 验证 sessions 加载成功
        val sessions = viewModel.allSessions.value
        assertEquals(1, sessions.size)
        assertEquals("session-1", sessions[0].sessionId)
    }

    @Test
    fun testSendMessage_successStreaming() = runTest {
        initViewModel(emptyList())
        testScheduler.advanceUntilIdle()

        // 模拟流式 API 成功吐出字
        doAnswer { invocation ->
            val listener = invocation.getArgument<StreamResponseListener>(2)
            listener.onContent("我")
            listener.onContent("想")
            listener.onContent("吃")
            listener.onContent("小鱼干")
            listener.onComplete()
            null
        }.whenever(repository).getPetResponseWithPictureInfoStreaming(any(), any(), any())

        // 模拟返回 pictureInfo 并为 0 条未处理
        whenever(repository.consumeLastPictureInfo()).thenReturn(PictureInfo(true, "小鱼干"))
        whenever(repository.getUnprocessedChatsCount()).thenReturn(0)

        // 发送用户消息
        viewModel.sendMessage("你喜欢吃什么？")
        testScheduler.advanceUntilIdle()

        // 1. 验证用户消息和 AI 最终回复均已保存到数据库
        argumentCaptor<ChatMessage>().apply {
            verify(repository, times(2)).saveChatMessage(capture(), eq(PetType.CAT))
            assertEquals("你喜欢吃什么？", firstValue.content)
            assertEquals("user", firstValue.role)
            assertEquals("我想吃小鱼干", secondValue.content)
            assertEquals("assistant", secondValue.role)
        }

        // 2. 验证 ViewModel 状态
        val state = viewModel.chatUiState.value as ChatUiState.Ready
        assertEquals(2, state.chatHistory.size)
        assertEquals("你喜欢吃什么？", state.chatHistory[0].content)
        assertEquals("我想吃小鱼干", state.chatHistory[1].content)
        
        // 验证消费了 PictureInfo 缓存
        val consumedPic = viewModel.consumeLastPictureInfo()
        assertNotNull(consumedPic)
        assertTrue(consumedPic!!.isPictureNeeded)
        assertEquals("小鱼干", consumedPic.pictureDescription)
    }

    @Test
    fun testSendMessage_errorStreamingFallback() = runTest {
        initViewModel(emptyList())
        testScheduler.advanceUntilIdle()

        // 模拟流式 API 网络报错
        doAnswer { invocation ->
            val listener = invocation.getArgument<StreamResponseListener>(2)
            listener.onError(RuntimeException("连接中断"))
            null
        }.whenever(repository).getPetResponseWithPictureInfoStreaming(any(), any(), any())

        viewModel.sendMessage("你好")
        testScheduler.advanceUntilIdle()

        // 验证是否保存了兜底错误信息
        argumentCaptor<ChatMessage>().apply {
            verify(repository, times(2)).saveChatMessage(capture(), eq(PetType.CAT))
            assertEquals("你好", firstValue.content)
            assertEquals("出错了喵，请稍后再试。", secondValue.content)
            assertEquals("assistant", secondValue.role)
        }

        val state = viewModel.chatUiState.value as ChatUiState.Ready
        assertEquals(2, state.chatHistory.size)
        assertEquals("出错了喵，请稍后再试。", state.chatHistory[1].content)
    }

    @Test
    fun testSwitchToSession() = runTest {
        initViewModel(emptyList())
        testScheduler.advanceUntilIdle()

        // 注入现有的会话列表
        val session1 = SessionInfo("session-1", PetType.CAT, "布丁", "最近聊天", 100L)
        val session2 = SessionInfo("session-2", PetType.DOG, "大白", "狗狗对话", 200L)
        whenever(sessionManager.getAllSessions()).thenReturn(listOf(session1, session2))
        
        // 加载全部会话
        viewModel.loadAllSessions()
        testScheduler.advanceUntilIdle()
        
        // 模拟切换目标会话的聊天历史
        val newSessionHistory = listOf(
            ChatEntity(id = 10L, content = "狗狗好", role = "user", petType = "DOG", sessionId = "session-2")
        )
        whenever(sessionManager.getSessionMessages("session-2", PetType.DOG)).thenReturn(newSessionHistory)

        // 模拟切换会话后 SessionManager.currentSessionId 会更新
        whenever(sessionManager.currentSessionId).thenReturn("session-2")

        // 切换到 session-2
        viewModel.switchToSession("session-2")
        testScheduler.advanceUntilIdle()

        // 验证 SessionManager 的 Session ID 已经被更新
        verify(sessionManager).setCurrentSessionId("session-2")

        // 验证 ViewModel 状态已经成功更新为 session-2 关联 of petType 狗狗与相应的聊天历史
        val state = viewModel.chatUiState.value as ChatUiState.Ready
        assertEquals(PetType.DOG, state.currentPetType)
        assertEquals(1, state.chatHistory.size)
        assertEquals("狗狗好", state.chatHistory[0].content)
    }

    @Test
    fun testSendMessage_triggerAnalyzeChatsWhenThresholdExceeded() = runTest {
        initViewModel(emptyList())
        testScheduler.advanceUntilIdle()

        // 模拟流式 API 成功吐出字并完成
        doAnswer { invocation ->
            val listener = invocation.getArgument<StreamResponseListener>(2)
            listener.onContent("猫咪回复")
            listener.onComplete()
            null
        }.whenever(repository).getPetResponseWithPictureInfoStreaming(any(), any(), any())

        // 模拟未处理条数达到 10 条（满足 >= 10 触发分析的阈值）
        whenever(repository.getUnprocessedChatsCount()).thenReturn(10)

        viewModel.sendMessage("你好")
        testScheduler.advanceUntilIdle()

        // 验证保存了用户消息和 AI 回复
        verify(repository, times(2)).saveChatMessage(any(), eq(PetType.CAT))

        // 验证因为达到未处理阈值 10，自动触发了聊天记录分析
        verify(repository).analyzeChats()
    }

    @Test
    fun testSendMessage_noAnalyzeChatsWhenThresholdNotExceeded() = runTest {
        initViewModel(emptyList())
        testScheduler.advanceUntilIdle()

        // 模拟流式 API 成功吐出字并完成
        doAnswer { invocation ->
            val listener = invocation.getArgument<StreamResponseListener>(2)
            listener.onContent("猫咪回复")
            listener.onComplete()
            null
        }.whenever(repository).getPetResponseWithPictureInfoStreaming(any(), any(), any())

        // 模拟未处理条数只有 9 条（不满足 >= 10 触发分析的阈值）
        whenever(repository.getUnprocessedChatsCount()).thenReturn(9)

        viewModel.sendMessage("你好")
        testScheduler.advanceUntilIdle()

        // 验证保存了消息
        verify(repository, times(2)).saveChatMessage(any(), eq(PetType.CAT))

        // 验证未处理聊天记录不足 10 条，不会调用聊天分析方法
        verify(repository, never()).analyzeChats()
    }
}

