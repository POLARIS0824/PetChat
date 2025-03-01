package com.example.chat

import com.example.chat.data.ChatDao
import com.example.chat.data.ChatEntity
import com.example.chat.data.ChatAnalysisEntity
import com.example.chat.model.ChatMessage
import com.example.chat.model.PetTypes
import com.example.chat.model.PictureInfo
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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
     * 处理未处理的聊天记录并生成分析
     */
    suspend fun processUnprocessedChats() {
        val unprocessedCount = chatDao.getUnprocessedChatsCount()
        if (unprocessedCount >= 10) {
            val chats = chatDao.getUnprocessedChats()
            val petType = PetTypes.valueOf(chats.first().petType)
            
            val systemPrompt = """分析以下对话记录，执行以下任务：
                1. 去除重复或相似的对话
                2. 提取重要的用户偏好和兴趣点
                3. 总结用户与宠物的互动模式
                4. 返回JSON格式的分析结果
                格式：{
                    "summary": "总体分析",
                    "preferences": ["偏好1", "偏好2"],
                    "patterns": ["模式1", "模式2"]
                }
            """.trimIndent()

            val chatHistory = chats.joinToString("\n") { 
                "${if (it.isFromUser) "用户" else "宠物"}：${it.content}" 
            }

            try {
                val response = getPetResponse(petType, chatHistory)
                
                // 解析AI返回的JSON结果
                try {
                    val analysis = gson.fromJson(response, ChatAnalysisResult::class.java)
                    
                    // 保存分析结果
                    chatDao.insertAnalysis(ChatAnalysisEntity(
                        petType = petType.name,
                        summary = analysis.summary,
                        preferences = gson.toJson(analysis.preferences),
                        patterns = gson.toJson(analysis.patterns)
                    ))
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 标记消息为已处理
                chatDao.update(chats.map { it.copy(isProcessed = true) })
                
                // 清理旧消息
                val oneWeekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
                chatDao.deleteOldProcessedChats(oneWeekAgo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
     * 调用AI API获取宠物回复
     * @param petType 当前选择的宠物类型
     * @param message 用户输入的消息
     * @return String AI的回复内容
     */
    suspend fun getPetResponse(petType: PetTypes, message: String): String {
        // 先获取增强的提示词
        val enhancedPrompt = getEnhancedPrompt(petType)
        
        // 然后使用 suspendCoroutine 处理网络请求
        return suspendCoroutine { continuation ->
            val requestBody = DeepseekRequest(
                messages = listOf(
                    Message("system", enhancedPrompt),  // 使用已获取的提示词
                    Message("user", message)
                ),
                model = "deepseek-chat",
                temperature = 1.0,
            )

            val request = Request.Builder()
                .url(BASE_URL)
                .header("Authorization", "Bearer $API_KEY")
                .post(gson.toJson(requestBody).toRequestBody(JSON))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            continuation.resumeWithException(IOException("Unexpected code $response"))
                            return
                        }

                        try {
                            val responseBody = response.body?.string()
                            val deepseekResponse = gson.fromJson(responseBody, DeepseekResponse::class.java)
                            continuation.resume(deepseekResponse.choices[0].message.content)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
    }

    /**
     * 获取未处理的聊天记录数量
     */
    suspend fun getUnprocessedChatsCount(): Int {
        return chatDao.getUnprocessedChatsCount()
    }

    /**
     * 保存聊天消息到本地数据库
     * @param message 要保存的聊天消息
     * @param petType 当前的宠物类型
     */
    suspend fun saveChatMessage(message: ChatMessage, petType: PetTypes) {
        chatDao.insert(
            ChatEntity(
            content = message.content,
            isFromUser = message.isFromUser,
            petType = petType.name
        )
        )
    }

    /**
     * 发送API请求
     */
    private suspend fun makeApiRequest(request: DeepseekRequest): DeepseekResponse {
        val requestBody = gson.toJson(request).toRequestBody(JSON)
        val requestBuilder = Request.Builder()
            .url(BASE_URL)
            .header("Authorization", "Bearer $API_KEY")
            .post(requestBody)
        
        val response = client.newCall(requestBuilder.build()).execute()
        return gson.fromJson(response.body?.string(), DeepseekResponse::class.java)
    }
}

/**
 * API请求的数据类
 */
data class DeepseekRequest(
    val messages: List<Message>,    // 对话消息列表
    val model: String,              // 使用的模型名称
    val temperature: Double,        // 回复的随机性参数
    val max_tokens: Int? = null     // 最大返回长度
)

/**
 * API消息的数据类
 */
data class Message(
    val role: String,    // 消息角色（system/user/assistant）
    val content: String  // 消息内容
)

/**
 * API响应的数据类
 */
data class DeepseekResponse(
    val choices: List<Choice>  // API返回的选项列表
)

/**
 * API响应选项的数据类
 */
data class Choice(
    val message: Message  // 选中的回复消息
)

/**
 * AI分析结果的数据类
 */
private data class ChatAnalysisResult(
    val summary: String,
    val preferences: List<String>,
    val patterns: List<String>
)