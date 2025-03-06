package com.example.chat

import android.util.Log
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 宠物聊天的数据仓库类
 * 负责处理所有的数据操作，包括API调用和本地数据库操作
 */
class PetChatRepository private constructor(
    private val chatDao: ChatDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)  // 连接超时时间
        .readTimeout(60, TimeUnit.SECONDS)     // 读取超时时间
        .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时时间
        .build(),
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
    private val API_KEY = "sk-cfa895f6201a4c6ab6b0036bf14ddeb4"  // API密钥
    private val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"  // API基础URL

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
    private val contextMessageLimit = 3  // 只保留最近5条消息作为上下文

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
            try {
                // 记录请求内容
                val requestJson = gson.toJson(request)
                Log.d("API_REQUEST", "请求体: $request")

                val requestBody = requestJson.toRequestBody(JSON)

                // 使用完整的API URL
                val apiUrl = "$BASE_URL/chat/completions"
                Log.d("API_REQUEST", "请求URL: $apiUrl")

                val httpRequest = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val requestBodyLog = gson.toJson(requestBody)
                Log.d("API_REQUEST", "请求体: $requestBodyLog")

                Log.d("API_REQUEST", "请求头: ${httpRequest.headers}")

                client.newCall(httpRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("API_ERROR", "请求失败: ${e.message}", e)
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string()
                            Log.d("API_RESPONSE", "状态码: ${response.code}")
                            Log.d("API_RESPONSE", "响应体: $responseBody")

                            if (!response.isSuccessful) {
                                Log.e("API_ERROR", "API错误: ${response.code} $responseBody")
                                continuation.resumeWithException(
                                    IOException("API请求失败: ${response.code} $responseBody")
                                )
                                return
                            }

                            if (responseBody == null) {
                                Log.e("API_ERROR", "响应体为空")
                                continuation.resumeWithException(IOException("响应体为空"))
                                return
                            }

                            // 解析响应
                            val apiResponse = gson.fromJson(responseBody, DeepseekResponse::class.java)
                            Log.d("API_RESPONSE", "解析后的响应: $apiResponse")
                            continuation.resume(apiResponse)
                        } catch (e: Exception) {
                            Log.e("API_ERROR", "解析错误: ${e.message}", e)
                            continuation.resumeWithException(e)
                        } finally {
                            response.close()
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e("API_ERROR", "请求构建错误: ${e.message}", e)
                e.printStackTrace()
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * 分析未处理的聊天记录
     * 当未处理消息达到10条时调用此方法
     */
    suspend fun analyzeChats() {
        val unprocessedChats = chatDao.getUnprocessedChats()
        Log.d("API_CHAT_ANALYSIS", "未处理聊天记录：$unprocessedChats")

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
            model = "deepseek-r1",  // 添加model参数
            messages = listOf(
                Message("assistant", "我是一个聊天分析助手，可以帮你分析聊天记录。"),
                Message("user", analysisPrompt)
            )
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
    suspend fun getPetResponse(petType: PetTypes, userMessage: String): String {
        try {
            Log.d("PET_RESPONSE", "开始获取宠物回复，宠物类型: $petType, 用户消息: $userMessage")

            // 获取增强的提示词
            val enhancedPrompt = getEnhancedPrompt(petType)
            Log.d("PET_RESPONSE", "增强提示词: $enhancedPrompt")

            // 获取最近的对话历史（限制数量）
            val recentMessages = chatDao.getRecentSessionMessages(
                currentSessionId,
                petType.name,
                contextMessageLimit
            )
            Log.d("PET_RESPONSE", "获取到${recentMessages.size}条历史消息")

            // 构建消息列表
            val messages = mutableListOf<Message>()

            // 添加助手角色的系统提示（作为第一条消息）
            messages.add(Message("user", enhancedPrompt))

            // 处理历史消息，确保用户和助手消息交替出现
            val processedMessages = recentMessages
                .distinctBy { "${it.role}:${it.content}" }
                .sortedBy { it.timestamp }
                .groupBy { it.isFromUser } // 按用户/助手分组

            // 构建交替的消息序列
            val userMessages = processedMessages[true] ?: listOf()
            val assistantMessages = processedMessages[false] ?: listOf()

            // 按时间顺序交替添加消息
            val maxIndex = maxOf(userMessages.size, assistantMessages.size)
            for (i in 0 until maxIndex) {
                if (i < assistantMessages.size) {
                    messages.add(Message("assistant", assistantMessages[i].content))
                }
                if (i < userMessages.size) {
                    messages.add(Message("user", userMessages[i].content))
                }
            }

            // 添加当前用户消息
            messages.add(Message("user", userMessage))
            Log.d("PET_RESPONSE", "构建了${messages.size}条消息")

            // 构建请求
            val request = DeepseekRequest(
                model = "deepseek-r1",
                messages = messages
            )

            // 调用API获取响应
            Log.d("PET_RESPONSE", "开始调用API")
            val response = makeApiRequest(request)

            val responseContent = response.choices.firstOrNull()?.message?.content
            Log.d("PET_RESPONSE", "API响应内容: $responseContent")

            return responseContent ?: throw IOException("AI响应为空")
        } catch (e: Exception) {
            Log.e("PET_RESPONSE", "获取宠物回复出错", e)
            e.printStackTrace()
            return "抱歉，我现在有点累了，待会再聊吧。" // 返回友好的错误信息
        }
    }
}

