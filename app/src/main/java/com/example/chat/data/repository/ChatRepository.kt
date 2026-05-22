package com.example.chat.data.repository

import android.util.Log
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatEntity
import com.example.chat.model.ChatMessage
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.Message
import com.example.chat.model.PetType
import com.example.chat.model.StreamResponseListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val apiService: ChatApiService,
    private val sessionManager: SessionManager,
    private val promptBuilder: PromptBuilder,
    private val pictureInfoParser: PictureInfoParser,
    private val analysisUseCase: ChatAnalysisUseCase,
    private val settingsManager: SettingsManager,
) {

    companion object {
        private const val CONTEXT_MESSAGE_LIMIT = 3
        private const val SUMMARY_THRESHOLD = 20
        private const val IMPORTANT_MESSAGE_LENGTH = 50
    }

    // region Message Building

    private suspend fun buildMessages(petType: PetType, userMessage: String): List<Message> {
        val enhancedPrompt = promptBuilder.build(petType)
        val recentMessages = chatDao.getRecentSessionMessages(
            sessionManager.currentSessionId, petType.name, CONTEXT_MESSAGE_LIMIT
        )

        val messages = mutableListOf<Message>()
        messages.add(Message("system", enhancedPrompt))

        recentMessages
            .filter { it.content.isNotBlank() } // 过滤掉由于故障等产生的空白历史信息
            .distinctBy { "${it.role}:${it.content}" }
            .sortedBy { it.timestamp }
            .forEach { messages.add(Message(it.role, it.content)) }

        messages.add(Message("user", userMessage))
        return messages
    }

    // endregion

    // region API Calls

    suspend fun getPetResponse(petType: PetType, userMessage: String): String {
        return try {
            val messages = buildMessages(petType, userMessage)
            val config = settingsManager.getConfig()
            val effectiveModel = config.model.trim().takeIf { it.isNotBlank() }
                ?: SettingsManager.DEFAULT_MODEL
            val request = DeepseekRequest(model = effectiveModel, messages = messages)
            val response = apiService.makeApiRequest(request)
            response.choices?.firstOrNull()?.message?.content
                ?: throw IllegalStateException("AI响应为空")
        } catch (e: Exception) {
            Log.e("PET_RESPONSE", "获取宠物回复出错", e)
            "抱歉，我现在有点累了，待会再聊吧。"
        }
    }

    suspend fun getPetResponseStreaming(
        petType: PetType,
        userMessage: String,
        listener: StreamResponseListener
    ) {
        try {
            val messages = buildMessages(petType, userMessage)
            val config = settingsManager.getConfig()
            val effectiveModel = config.model.trim().takeIf { it.isNotBlank() }
                ?: SettingsManager.DEFAULT_MODEL
            val request = DeepseekRequest(model = effectiveModel, messages = messages, stream = true)
            apiService.makeStreamingApiRequest(request).collect { content ->
                listener.onContent(content)
            }
            listener.onComplete()
        } catch (e: Exception) {
            listener.onError(e)
        }
    }

    suspend fun getPetResponseWithPictureInfoStreaming(
        petType: PetType,
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
                val pictureInfo = pictureInfoParser.extract(responseBuffer.toString())
                pictureInfoParser.setLastPictureInfo(pictureInfo.second)
                listener.onComplete()
            }

            override fun onError(e: Exception) {
                listener.onError(e)
            }
        }
        getPetResponseStreaming(petType, message, wrapperListener)
    }

    // endregion

    // region Chat Persistence

    suspend fun saveChatMessage(message: ChatMessage, petType: PetType) {
        if (message.content.isBlank()) return // 防御性保护：防止保存空白内容消息到数据库
        val entity = ChatEntity(
            content = message.content,
            petType = petType.name,
            sessionId = sessionManager.currentSessionId,
            role = message.role,
            isImportant = isMessageImportant(message.content)
        )
        chatDao.insert(entity)

        val unprocessedCount = chatDao.getUnprocessedChatsCount()
        if (unprocessedCount > SUMMARY_THRESHOLD) analysisUseCase.summarizeConversation()
    }

    private fun isMessageImportant(content: String): Boolean {
        return content.contains("?") || content.contains("!") ||
                content.length > IMPORTANT_MESSAGE_LENGTH || content.contains("喜欢") ||
                content.contains("不喜欢") || content.contains("想要")
    }

    suspend fun getUnprocessedChatsCount(): Int = chatDao.getUnprocessedChatsCount()

    // endregion

    // region Delegation

    fun consumeLastPictureInfo() = pictureInfoParser.consumeLastPictureInfo()

    suspend fun analyzeChats() = analysisUseCase.analyzeChats()

    // endregion
}
