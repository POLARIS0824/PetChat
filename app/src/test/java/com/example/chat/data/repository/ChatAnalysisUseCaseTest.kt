package com.example.chat.data.repository

import android.util.Log
import com.example.chat.data.ChatDatabase
import com.example.chat.data.dao.AnalysisDao
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatAnalysisEntity
import com.example.chat.data.entity.ChatEntity
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.DeepseekResponse
import com.example.chat.model.Message
import com.example.chat.model.PetType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.io.IOException

class ChatAnalysisUseCaseTest {

    private val chatDao: ChatDao = mock()
    private val analysisDao: AnalysisDao = mock()
    private val apiService: ChatApiService = mock()
    private val promptBuilder: PromptBuilder = mock()
    private val database: ChatDatabase = mock()
    private val settingsManager: SettingsManager = mock()

    private lateinit var useCase: ChatAnalysisUseCase
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setUp() {
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.e(any(), any(), any()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(any(), any()) }.thenReturn(0)

        // 让 database.runInTransaction { runnable } 直接执行 runnable
        doAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
            null
        }.whenever(database).runInTransaction(any<Runnable>())

        // Stub settingsManager.getConfig()
        whenever(settingsManager.getConfig()).thenReturn(
            ApiConfig(
                baseUrl = "https://example.com/api",
                apiKey = "test-api-key",
                model = "deepseek-v3"
            )
        )

        useCase = ChatAnalysisUseCase(
            chatDao = chatDao,
            analysisDao = analysisDao,
            apiService = apiService,
            promptBuilder = promptBuilder,
            database = database,
            settingsManager = settingsManager
        )
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun testAnalyzeChats_thresholdNotMet() = runTest {
        // 当未处理聊天记录数不足 10 条时（分析的阈值）
        val chats = List(5) {
            ChatEntity(id = it.toLong(), content = "内容$it", petType = PetType.CAT.name, sessionId = "session-1")
        }
        whenever(chatDao.getUnprocessedChats()).thenReturn(chats)

        useCase.analyzeChats()

        // 验证不会调用 API，也不会写入任何数据
        verify(apiService, never()).makeApiRequest(any())
        verify(analysisDao, never()).insertBlocking(any())
        verify(chatDao, never()).updateBlocking(any())
    }

    @Test
    fun testAnalyzeChats_thresholdMetAndSucceeds() = runTest {
        // 当未处理聊天记录数达到 10 条（如 10 条 CAT 的对话）
        val chats = List(10) {
            ChatEntity(id = it.toLong(), content = "猫咪对话$it", petType = PetType.CAT.name, sessionId = "session-1", role = "user")
        }
        whenever(chatDao.getUnprocessedChats()).thenReturn(chats)

        // Stub 提示词构建器和 API 响应 (Deepseek 返回合规的 JSON)
        whenever(promptBuilder.build(any())).thenReturn("布丁的系统提示词")
        
        val apiJsonResult = """
            {
                "summary": "用户对猫咪表现出浓厚的喜爱，并多次喂食小鱼干。",
                "preferences": ["喜欢鱼干", "喜欢傲娇"],
                "patterns": ["主动提问"]
            }
        """.trimIndent()

        val apiResponse = DeepseekResponse(
            choices = listOf(
                DeepseekResponse.Choice(
                    message = Message(role = "assistant", content = apiJsonResult)
                )
            )
        )
        whenever(apiService.makeApiRequest(any())).thenReturn(apiResponse)

        useCase.analyzeChats()

        // 1. 验证发起了 API 请求
        verify(apiService).makeApiRequest(argThat {
            model == "deepseek-v3" && messages.size == 2
        })

        // 2. 验证保存了解析后的用户画像分析
        argumentCaptor<ChatAnalysisEntity>().apply {
            verify(analysisDao).insertBlocking(capture())
            assertEquals(PetType.CAT.name, firstValue.petType)
            assertEquals("用户对猫咪表现出浓厚的喜爱，并多次喂食小鱼干。", firstValue.summary)
            assertTrue(firstValue.preferences.contains("喜欢鱼干"))
            assertTrue(firstValue.patterns.contains("主动提问"))
        }

        // 3. 验证聊天记录状态全部更新为已处理 (isProcessed = true)
        argumentCaptor<List<ChatEntity>>().apply {
            verify(chatDao).updateBlocking(capture())
            assertEquals(10, firstValue.size)
            assertTrue(firstValue.all { it.isProcessed })
        }
    }

    @Test
    fun testSummarizeConversation_thresholdNotMet() = runTest {
        // 当未处理聊天记录数不足 10 条时，不进行总结
        val chats = List(8) {
            ChatEntity(id = it.toLong(), content = "普通聊天$it", petType = PetType.CAT.name, sessionId = "session-1")
        }
        whenever(chatDao.getUnprocessedChats()).thenReturn(chats)

        useCase.summarizeConversation()

        verify(apiService, never()).makeApiRequest(any())
        verify(chatDao, never()).insertBlocking(any())
        verify(chatDao, never()).updateBlocking(any())
    }

    @Test
    fun testSummarizeConversation_thresholdMetAndSucceeds() = runTest {
        // 当未处理聊天记录数达到 10 条（如 12 条 CAT 对话）
        val chats = List(12) {
            ChatEntity(
                id = it.toLong(),
                content = "消息$it",
                petType = PetType.CAT.name,
                sessionId = "session-xyz",
                role = if (it % 2 == 0) "user" else "assistant"
            )
        }
        whenever(chatDao.getUnprocessedChats()).thenReturn(chats)
        whenever(promptBuilder.build(any())).thenReturn("基础提示词")

        // Stub 摘要大模型的响应
        val apiResponse = DeepseekResponse(
            choices = listOf(
                DeepseekResponse.Choice(
                    message = Message(role = "assistant", content = "用户与猫咪亲切互动，讨论了日常琐事。")
                )
            )
        )
        whenever(apiService.makeApiRequest(any())).thenReturn(apiResponse)

        useCase.summarizeConversation()

        // 1. 验证生成了摘要的 API 调用
        verify(apiService).makeApiRequest(argThat {
            messages.any { it.content.contains("请对以下对话进行摘要") }
        })

        // 2. 验证往数据库中插入了一条特殊的摘要消息
        argumentCaptor<ChatEntity>().apply {
            verify(chatDao).insertBlocking(capture())
            assertEquals("【对话摘要】用户与猫咪亲切互动，讨论了日常琐事。", firstValue.content)
            assertEquals("system", firstValue.role)
            assertEquals(PetType.CAT.name, firstValue.petType)
            assertEquals("session-xyz", firstValue.sessionId)
            assertTrue(firstValue.isImportant)
            assertTrue(firstValue.isSummary)
            assertTrue(firstValue.isProcessed)
        }

        // 3. 验证原有的未处理消息状态均更新为已处理 (isProcessed = true)
        argumentCaptor<List<ChatEntity>>().apply {
            verify(chatDao).updateBlocking(capture())
            assertEquals(12, firstValue.size)
            assertTrue(firstValue.all { it.isProcessed })
        }
    }

    @Test
    fun testAnalyzeChats_apiFailureGracefulFallback() = runTest {
        // 当未处理聊天记录数达到 10 条，但是 API 发生异常时
        val chats = List(10) {
            ChatEntity(id = it.toLong(), content = "猫咪对话$it", petType = PetType.CAT.name, sessionId = "session-1", role = "user")
        }
        whenever(chatDao.getUnprocessedChats()).thenReturn(chats)
        whenever(apiService.makeApiRequest(any())).thenThrow(RuntimeException("API故障"))

        // 调用分析方法，验证异常被捕获且没有向上抛出
        useCase.analyzeChats()

        // 验证没有写入任何分析数据，也没有更新任何已处理状态
        verify(analysisDao, never()).insertBlocking(any())
        verify(chatDao, never()).updateBlocking(any())
    }

    @Test
    fun testSummarizeConversation_apiFailureGracefulFallback() = runTest {
        // 当未处理聊天记录数达到 10 条，但是在事务中发生数据库操作异常时
        val chats = List(12) {
            ChatEntity(id = it.toLong(), content = "消息$it", petType = PetType.CAT.name, sessionId = "session-xyz", role = "user")
        }
        whenever(chatDao.getUnprocessedChats()).thenReturn(chats)
        whenever(promptBuilder.build(any())).thenReturn("基础提示词")

        // 模拟 API 正常返回，但数据库插入/更新事务崩溃
        val apiResponse = DeepseekResponse(
            choices = listOf(
                DeepseekResponse.Choice(
                    message = Message(role = "assistant", content = "对话摘要")
                )
            )
        )
        whenever(apiService.makeApiRequest(any())).thenReturn(apiResponse)
        whenever(database.runInTransaction(any<Runnable>())).thenThrow(RuntimeException("数据库故障"))

        // 调用摘要方法，验证异常被捕获且没有向上抛出
        useCase.summarizeConversation()
    }

    @Test
    fun testAnalyzeChats_mixedPetTypesBelowThresholdIndividual() = runTest {
        // 总未处理数达到 15 条（>= 10），但 CAT 有 8 条，DOG 有 7 条，各自都未达 10 条
        val chats = List(8) {
            ChatEntity(id = it.toLong(), content = "猫咪对话$it", petType = PetType.CAT.name, sessionId = "session-1", role = "user")
        } + List(7) {
            ChatEntity(id = (it + 8).toLong(), content = "狗狗对话$it", petType = PetType.DOG.name, sessionId = "session-1", role = "user")
        }
        whenever(chatDao.getUnprocessedChats()).thenReturn(chats)

        useCase.analyzeChats()

        // 验证不会调用 API，不会保存任何分析记录，也不会更新已处理状态
        verify(apiService, never()).makeApiRequest(any())
        verify(analysisDao, never()).insertBlocking(any())
        verify(chatDao, never()).updateBlocking(any())
    }

    @Test
    fun testAnalyzeChats_mixedPetTypesOneAboveThreshold() = runTest {
        // 总未处理数达到 17 条，其中 CAT 有 12 条（>= 10），DOG 有 5 条（< 10）
        val chats = List(12) {
            ChatEntity(id = it.toLong(), content = "猫咪对话$it", petType = PetType.CAT.name, sessionId = "session-1", role = "user")
        } + List(5) {
            ChatEntity(id = (it + 12).toLong(), content = "狗狗对话$it", petType = PetType.DOG.name, sessionId = "session-1", role = "user")
        }
        whenever(chatDao.getUnprocessedChats()).thenReturn(chats)
        whenever(promptBuilder.build(any())).thenReturn("提示词")

        val apiJsonResult = """
            {
                "summary": "猫咪互动总结",
                "preferences": ["喜欢猫罐头"],
                "patterns": ["调皮行为"]
            }
        """.trimIndent()

        val apiResponse = DeepseekResponse(
            choices = listOf(
                DeepseekResponse.Choice(
                    message = Message(role = "assistant", content = apiJsonResult)
                )
            )
        )
        whenever(apiService.makeApiRequest(any())).thenReturn(apiResponse)

        useCase.analyzeChats()

        // 1. 验证发起了 API 请求以分析 CAT 聊天
        verify(apiService).makeApiRequest(argThat {
            messages.any { it.content.contains("猫咪对话") }
        })
        // 并且绝对不能包含 DOG 聊天的分析请求
        verify(apiService, never()).makeApiRequest(argThat {
            messages.any { it.content.contains("狗狗对话") }
        })

        // 2. 验证仅保存了 CAT 的分析结果
        argumentCaptor<ChatAnalysisEntity>().apply {
            verify(analysisDao).insertBlocking(capture())
            assertEquals(PetType.CAT.name, firstValue.petType)
            assertEquals("猫咪互动总结", firstValue.summary)
        }

        // 3. 验证仅将 CAT 的 12 条聊天记录标记为已处理 (isProcessed = true)
        argumentCaptor<List<ChatEntity>>().apply {
            verify(chatDao).updateBlocking(capture())
            assertEquals(12, firstValue.size)
            assertTrue(firstValue.all { it.petType == PetType.CAT.name && it.isProcessed })
        }
    }
}

