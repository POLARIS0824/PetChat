package com.example.chat.data.repository

import android.util.Log
import com.example.chat.data.ChatDao
import com.example.chat.data.ChatAnalysisEntity
import com.example.chat.data.ChatEntity
import com.example.chat.model.ChatAnalysisResult
import com.example.chat.model.ChatMessage
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.Message
import com.example.chat.model.PetTypes
import com.example.chat.model.PictureInfo
import com.example.chat.model.SessionInfo
import com.example.chat.model.StreamResponseListener
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val apiService: ChatApiService,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val model = com.example.chat.BuildConfig.PETCHAT_MODEL.trim().ifBlank { "deepseek-v3" }

    private var currentSessionId: String = UUID.randomUUID().toString()
    private val contextMessageLimit = 3

    // region Prompt

    private suspend fun getEnhancedPrompt(petType: PetTypes): String {
        val basePrompt = PromptConfig.prompts[petType] ?: ""
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

    // endregion

    // region Message Building

    private suspend fun buildMessages(petType: PetTypes, userMessage: String): List<Message> {
        val enhancedPrompt = getEnhancedPrompt(petType)
        val recentMessages = chatDao.getRecentSessionMessages(
            currentSessionId, petType.name, contextMessageLimit
        )

        val messages = mutableListOf<Message>()
        messages.add(Message("user", enhancedPrompt))

        val processedMessages = recentMessages
            .distinctBy { "${it.role}:${it.content}" }
            .sortedBy { it.timestamp }
            .groupBy { it.isFromUser }

        val userMessages = processedMessages[true] ?: listOf()
        val assistantMessages = processedMessages[false] ?: listOf()

        val maxIndex = maxOf(userMessages.size, assistantMessages.size)
        for (i in 0 until maxIndex) {
            if (i < assistantMessages.size) messages.add(Message("assistant", assistantMessages[i].content))
            if (i < userMessages.size) messages.add(Message("user", userMessages[i].content))
        }

        messages.add(Message("user", userMessage))
        return messages
    }

    // endregion

    // region API Calls

    suspend fun getPetResponse(petType: PetTypes, userMessage: String): String {
        return try {
            val messages = buildMessages(petType, userMessage)
            val request = DeepseekRequest(model = model, messages = messages)
            val response = apiService.makeApiRequest(request)
            response.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("AI响应为空")
        } catch (e: Exception) {
            Log.e("PET_RESPONSE", "获取宠物回复出错", e)
            "抱歉，我现在有点累了，待会再聊吧。"
        }
    }

    suspend fun getPetResponseStreaming(
        petType: PetTypes,
        userMessage: String,
        listener: StreamResponseListener
    ) {
        try {
            val messages = buildMessages(petType, userMessage)
            val request = DeepseekRequest(model = model, messages = messages, stream = true)
            apiService.makeStreamingApiRequest(request, listener)
        } catch (e: Exception) {
            listener.onError(e)
        }
    }

    suspend fun getPetResponseWithPictureInfoStreaming(
        petType: PetTypes,
        message: String,
        listener: StreamResponseListener
    ) {
        val wrapperListener = object : StreamResponseListener {
            private val responseBuffer = StringBuilder()

            override fun onContent(content: String) {
                responseBuffer.append(content)
                listener.onContent(content)
            }

            override fun onComplete() {
                val pictureInfo = extractPictureInfo(responseBuffer.toString())
                lastPictureInfo = pictureInfo.second
                listener.onComplete()
            }

            override fun onError(e: Exception) {
                listener.onError(e)
            }
        }
        getPetResponseStreaming(petType, message, wrapperListener)
    }

    // endregion

    // region Chat Analysis

    suspend fun analyzeChats() {
        val unprocessedChats = chatDao.getUnprocessedChats()
        if (unprocessedChats.size < 10) return

        val analysisPrompt = """
            请分析以下聊天记录，并提供:
            1. 对话总结
            2. 用户偏好和兴趣
            3. 主要互动模式

            聊天记录：
            ${unprocessedChats.joinToString("\n") {
                if (it.isFromUser) "用户: ${it.content}" else "宠物: ${it.content}"
            }}

            请用JSON格式返回，格式如下：
            {
                "summary": "对话总结",
                "preferences": ["偏好1", "偏好2"],
                "patterns": ["互动模式1", "互动模式2"]
            }
        """.trimIndent()

        val request = DeepseekRequest(
            model = model,
            messages = listOf(
                Message("system", "我是一个聊天分析助手，可以帮你分析聊天记录。"),
                Message("user", analysisPrompt)
            )
        )

        try {
            val response = apiService.makeApiRequest(request)
            val analysisText = response.choices.firstOrNull()?.message?.content ?: return
            val analysis = json.decodeFromString<ChatAnalysisResult>(analysisText)

            val analysisEntity = ChatAnalysisEntity(
                petType = unprocessedChats.first().petType,
                summary = analysis.summary,
                preferences = json.encodeToString(analysis.preferences),
                patterns = json.encodeToString(analysis.patterns)
            )
            chatDao.insertAnalysis(analysisEntity)
            chatDao.update(unprocessedChats.map { it.copy(isProcessed = true) })
        } catch (e: Exception) {
            Log.e("ANALYSIS", "分析聊天记录出错", e)
        }
    }

    // endregion

    // region Chat Persistence

    suspend fun saveChatMessage(message: ChatMessage, petType: PetTypes) {
        val entity = ChatEntity(
            content = message.content,
            isFromUser = message.isFromUser,
            petType = petType.name,
            sessionId = "default_${petType.name}",
            role = if (message.isFromUser) "user" else "assistant",
            isImportant = isMessageImportant(message.content)
        )
        chatDao.insert(entity)

        val unprocessedCount = chatDao.getUnprocessedChatsCount()
        if (unprocessedCount > 20) summarizeConversation()
    }

    private fun isMessageImportant(content: String): Boolean {
        return content.contains("?") || content.contains("!") ||
                content.length > 50 || content.contains("喜欢") ||
                content.contains("不喜欢") || content.contains("想要")
    }

    private suspend fun summarizeConversation() {
        val messages = chatDao.getUnprocessedChats()
        if (messages.size < 10) return

        val summaryPrompt = """
            请对以下对话进行摘要，提取关键信息，不超过100字：
            ${messages.joinToString("\n") {
                (if (it.isFromUser) "用户: " else "宠物: ") + it.content
            }}
        """.trimIndent()

        try {
            val summary = getPetResponse(PetTypes.valueOf(messages.first().petType), summaryPrompt)
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
            chatDao.update(messages.map { it.copy(isProcessed = true) })
        } catch (e: Exception) {
            Log.e("SUMMARY", "对话摘要出错", e)
        }
    }

    suspend fun getMessagesByPetType(petType: PetTypes): List<ChatEntity> {
        return try {
            chatDao.getMessagesByPetType(petType.name)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUnprocessedChatsCount(): Int = chatDao.getUnprocessedChatsCount()

    // endregion

    // region Picture Info

    private var lastPictureInfo: PictureInfo? = null

    fun consumeLastPictureInfo(): PictureInfo? {
        val info = lastPictureInfo
        lastPictureInfo = null
        return info
    }

    private fun extractPictureInfo(response: String): Pair<String, PictureInfo> {
        val systemNoteStart = response.indexOf("<system_note>")
        val systemNoteEnd = response.indexOf("</system_note>")

        return if (systemNoteStart != -1 && systemNoteEnd != -1) {
            val cleanResponse = response.substring(0, systemNoteStart).trim()
            val jsonStr = response.substring(systemNoteStart + 13, systemNoteEnd)
            try {
                Pair(cleanResponse, json.decodeFromString<PictureInfo>(jsonStr))
            } catch (e: Exception) {
                Pair(cleanResponse, PictureInfo(false, ""))
            }
        } else {
            Pair(response, PictureInfo(false, ""))
        }
    }

    // endregion

    // region Session Management

    fun createNewSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        return currentSessionId
    }

    fun setCurrentSessionId(sessionId: String) {
        currentSessionId = sessionId
    }

    suspend fun getSessionMessages(petType: PetTypes, sessionId: String? = null): List<ChatEntity> {
        val targetSessionId = sessionId ?: currentSessionId
        return try {
            chatDao.getSessionMessages(targetSessionId, petType.name)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAllSessions(): List<SessionInfo> {
        return PetTypes.entries.map { petType ->
            val latestMessages = chatDao.getRecentSessionMessages(
                "default_${petType.name}", petType.name, 1
            )
            SessionInfo(
                sessionId = "default_${petType.name}",
                petType = petType,
                petName = getPetName(petType),
                lastMessage = latestMessages.firstOrNull()?.content ?: "",
                timestamp = latestMessages.firstOrNull()?.timestamp ?: System.currentTimeMillis()
            )
        }
    }

    private fun getPetName(petType: PetTypes): String = when (petType) {
        PetTypes.CAT -> "布丁"
        PetTypes.DOG -> "大白"
        PetTypes.HAMSTER -> "团绒"
        PetTypes.DOG2 -> "豆豆"
    }

    // endregion
}
