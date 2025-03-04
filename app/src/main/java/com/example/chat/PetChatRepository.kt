package com.example.chat

import com.example.chat.data.ChatDao
import com.example.chat.data.ChatEntity
import com.example.chat.data.ChatAnalysisEntity
import com.example.chat.model.ChatAnalysisResult
import com.example.chat.model.ChatMessage
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.DeepseekResponse
import com.example.chat.model.Message
import com.example.chat.model.PetTypes
import com.example.chat.model.PictureInfo
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 宠物聊天的数据仓库类
 * 负责处理所有的数据操作，包括API调用和本地数据库操作
 */
class PetChatRepository private constructor(
    private val chatDao: ChatDao,
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    companion object {
        @Volatile
        private var instance: PetChatRepository? = null

        fun getInstance(chatDao: ChatDao): PetChatRepository {
            return instance ?: synchronized(this) {
                instance ?: PetChatRepository(chatDao).also { instance = it }
            }
        }
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val API_KEY = "sk-df188b66229341b6aa6886c4d1853ff6"  // API密钥
    private val BASE_URL = "https://api.deepseek.com/v1/chat/completions"  // API基础URL

    /**
     * 宠物角色的系统提示词配置
     * 为不同的宠物类型定义其性格特征和行为模式
     */
    private val prompts = mapOf(
        PetTypes.CAT to """你现在是一只可爱的猫咪。你需要：
            1. 用猫咪的方式思考和回答
            2. 经常使用"喵"等拟声词
            3. 表现出对逗猫棒、猫粮和纸箱子的兴趣
            4. 偶尔表现出傲娇的性格
            5. 用简短的句子回应
            """,

        PetTypes.DOG to """你现在是一只忠诚的狗狗。你需要：
            1. 表现出对主人的热情和忠诚
            2. 经常使用"汪"等拟声词
            3. 对散步、玩球表现出极大兴趣
            4. 性格活泼开朗
            5. 表达方式要充满活力
            """
    )

    // 新增：当前会话ID
    private var currentSessionId: String = UUID.randomUUID().toString()

    // 新增：消息历史限制
    private val contextMessageLimit = 5  // 只保留最近5条消息作为上下文

    // 新增：系统消息压缩
    private val compressedPrompts = mapOf(
        PetTypes.CAT to "你是猫咪。用喵结尾。简短回复。偶尔傲娇。",
        PetTypes.DOG to "你是狗狗。用汪结尾。热情活泼。喜欢散步玩球。"
    )

    /**
     * 获取带图片信息的宠物回复
     * @param petType 当前选择的宠物类型
     * @param message 用户输入的消息
     * @return Pair<String, PictureInfo> 包含AI回复内容和图片信息
     */
    suspend fun getPetResponseWithPictureInfo(petType: PetTypes, message: String): Pair<String, PictureInfo> {
        val fullResponse = getPetResponse(petType, message)
        
        // 分离回复内容和系统指令部分
        val systemNoteStart = fullResponse.indexOf("<system_note>")
        val systemNoteEnd = fullResponse.indexOf("</system_note>")
        
        return if (systemNoteStart != -1 && systemNoteEnd != -1) {
            // 只返回系统指令之前的内容
            val response = fullResponse.substring(0, systemNoteStart).trim()
            val jsonStr = fullResponse.substring(systemNoteStart + 13, systemNoteEnd)
            
            try {
                val pictureInfo = gson.fromJson(jsonStr, PictureInfo::class.java)
                Pair(response, pictureInfo)
            } catch (e: Exception) {
                Pair(response, PictureInfo(false, ""))
            }
        } else {
            // 如果没有找到系统指令，返回完整响应和空图片信息
            Pair(fullResponse, PictureInfo(false, ""))
        }
    }

    /**
     * 获取带用户偏好的系统提示
     */
    private suspend fun getEnhancedPrompt(petType: PetTypes): String {
        val basePrompt = prompts[petType] ?: ""
        val analysis = chatDao.getLatestAnalysis(petType.name)
        
        return if (analysis != null) {
            """
            $basePrompt
            
            用户画像信息：
            总体分析：${analysis.summary}
            用户偏好：${analysis.preferences}
            互动模式：${analysis.patterns}
            
            请根据以上用户画像信息，调整你的回复风格和内容。
            """.trimIndent()
        } else {
            basePrompt
        }
    }

    /**
     * 发送API请求并获取响应
     */
    private suspend fun makeApiRequest(request: DeepseekRequest): DeepseekResponse {
        return suspendCoroutine { continuation ->
            val requestBody = gson.toJson(request).toRequestBody(JSON)

            val httpRequest = Request.Builder()
                .url(BASE_URL)
                .header("Authorization", "Bearer $API_KEY")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            continuation.resumeWithException(
                                IOException("API请求失败: ${response.code}")
                            )
                            return
                        }

                        try {
                            val responseBody = response.body?.string()
                            val deepseekResponse = gson.fromJson(responseBody, DeepseekResponse::class.java)
                            continuation.resume(deepseekResponse)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
    }

    /**
     * 分析未处理的聊天记录
     * 当未处理消息达到10条时调用此方法
     */
    suspend fun analyzeChats() {
        val unprocessedChats = chatDao.getUnprocessedChats()
        if (unprocessedChats.size < 10) return

        // 构建分析提示词
        val analysisPrompt = """
            请分析以下聊天记录，并提供:
            1. 对话总结
            2. 用户偏好和兴趣
            3. 主要互动模式
            
            聊天记录：
            ${unprocessedChats.joinToString("\n") { 
                if (it.isFromUser) "用户: ${it.content}" 
                else "宠物: ${it.content}" 
            }}
            
            请用JSON格式返回，格式如下：
            {
                "summary": "对话总结",
                "preferences": ["偏好1", "偏好2", ...],
                "patterns": ["互动模式1", "互动模式2", ...]
            }
        """.trimIndent()

        // 调用API进行分析
        val request = DeepseekRequest(
            messages = listOf(Message("user", analysisPrompt)),
            model = "deepseek-chat",  // 添加model参数
            temperature = 0.7,
            max_tokens = 1000
        )

        try {
            // 发送API请求
            val response = makeApiRequest(request)
            val analysisText = response.choices.firstOrNull()?.message?.content ?: return
            
            // 解析JSON响应
            val analysis = gson.fromJson(analysisText, ChatAnalysisResult::class.java)
            
            // 保存分析结果到数据库
            val analysisEntity = ChatAnalysisEntity(
                petType = unprocessedChats.first().petType,
                summary = analysis.summary,
                preferences = gson.toJson(analysis.preferences),
                patterns = gson.toJson(analysis.patterns)
            )
            chatDao.insertAnalysis(analysisEntity)
            
            // 将已分析的消息标记为已处理
            chatDao.update(unprocessedChats.map { it.copy(isProcessed = true) })
        } catch (e: Exception) {
            // 处理错误
            e.printStackTrace()
        }
    }

    /**
     * 获取压缩版的系统提示
     */
    private suspend fun getCompressedPrompt(petType: PetTypes): String {
        val basePrompt = compressedPrompts[petType] ?: ""
        val analysis = chatDao.getLatestAnalysis(petType.name)

        return if (analysis != null) {
            "$basePrompt 用户偏好:${analysis.summary.take(50)}"
        } else {
            basePrompt
        }
    }

    /**
     * 保存聊天消息并智能标记重要性
     */
    suspend fun saveChatMessage(message: ChatMessage, petType: PetTypes) {
        // 保存消息
        val entity = ChatEntity(
            content = message.content,
            isFromUser = message.isFromUser,
            petType = petType.name,
            sessionId = currentSessionId,
            role = if (message.isFromUser) "user" else "assistant",
            // 自动判断消息重要性
            isImportant = isMessageImportant(message.content)
        )

        val id = chatDao.insert(entity)

        // 如果消息数量超过限制，执行摘要
        val unprocessedCount = chatDao.getUnprocessedChatsCount()
        if (unprocessedCount > 20) {
            summarizeConversation()
        }
    }

    /**
     * 判断消息是否重要
     */
    private fun isMessageImportant(content: String): Boolean {
        // 简单实现：包含问号或感叹号的消息可能更重要
        return content.contains("?") || content.contains("!") ||
                content.length > 50 || content.contains("喜欢") ||
                content.contains("不喜欢") || content.contains("想要")
    }

    /**
     * 对话摘要，减少历史消息数量
     */
    private suspend fun summarizeConversation() {
        // 获取未处理的消息
        val messages = chatDao.getUnprocessedChats()
        if (messages.size < 10) return

        // 构建摘要提示词
        val summaryPrompt = """
            请对以下对话进行摘要，提取关键信息，不超过100字：
            ${
            messages.joinToString("\n") {
                (if (it.isFromUser) "用户: " else "宠物: ") + it.content
            }
        }
        """.trimIndent()

        try {
            // 调用API获取摘要
            val summary =
                getPetResponse(PetTypes.valueOf(messages.first().petType), summaryPrompt)

            // 创建摘要消息并标记为重要
            val summaryEntity = ChatEntity(
                content = "【对话摘要】$summary",
                isFromUser = false,
                petType = messages.first().petType,
                sessionId = currentSessionId,
                role = "system",
                isImportant = true,
                isProcessed = true
            )
            chatDao.insert(summaryEntity)

            // 标记已处理的消息
            chatDao.update(messages.map { it.copy(isProcessed = true) })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建新会话
     */
    fun createNewSession() {
        currentSessionId = UUID.randomUUID().toString()
    }

    /**
     * 获取当前会话的消息
     * @param petType 宠物类型
     * @return 当前会话的消息列表
     */
    suspend fun getSessionMessages(petType: PetTypes): List<ChatEntity> {
        return chatDao.getSessionMessages(currentSessionId, petType.name)
    }

    /**
     * 获取未处理消息的数量
     * @return 未处理消息的数量
     */
    suspend fun getUnprocessedChatsCount(): Int {
        return chatDao.getUnprocessedChatsCount()
    }

    /**
     * 调用AI API获取宠物回复
     * @param petType 当前选择的宠物类型
     * @param message 用户输入的消息
     * @return String AI的回复内容
     */
    suspend fun getPetResponse(petType: PetTypes, message: String): String {
        try {
            // 获取增强的提示词（压缩版）
            val enhancedPrompt = getCompressedPrompt(petType)

            // 获取最近的对话历史（限制数量）
            val recentMessages = chatDao.getRecentSessionMessages(
                currentSessionId,
                petType.name,
                contextMessageLimit
            )

            // 获取重要消息（关键上下文）
            val importantMessages = chatDao.getImportantMessages(currentSessionId)

            // 构建消息列表，优先添加系统提示和重要消息
            val messages = mutableListOf<Message>()
            messages.add(Message("system", enhancedPrompt))

            // 添加重要消息（如果不在最近消息中）
            val recentIds = recentMessages.map { it.id }
            importantMessages
                .filter { it.id !in recentIds }
                .forEach {
                    messages.add(Message(it.role, it.content))
                }

            // 添加最近消息
            recentMessages.forEach {
                messages.add(Message(it.role, it.content))
            }

            // 添加当前用户消息
            messages.add(Message("user", message))

            // 调用API获取响应
            val request = DeepseekRequest(
                messages = messages,
                model = "deepseek-chat",
                temperature = 1.0,
                max_tokens = 150  // 限制返回长度
            )

            val response = makeApiRequest(request)
            return response.choices.firstOrNull()?.message?.content ?: throw IOException("AI响应为空")
        } catch (e: Exception) {
            e.printStackTrace()
            return "抱歉，我现在有点累了，待会再聊吧。" // 返回友好的错误信息
        }
    }
}

