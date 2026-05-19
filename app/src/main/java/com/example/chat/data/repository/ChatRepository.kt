package com.example.chat.data.repository

import android.util.Log
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatEntity
import com.example.chat.model.ChatMessage
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.Message
import com.example.chat.model.PetTypes
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
) {
    private val model = com.example.chat.BuildConfig.PETCHAT_MODEL.trim().ifBlank { "deepseek-v3" }
    private val contextMessageLimit = 3

    // region Message Building

    private suspend fun buildMessages(petType: PetTypes, userMessage: String): List<Message> {
        val enhancedPrompt = promptBuilder.build(petType)
        val recentMessages = chatDao.getRecentSessionMessages(
            sessionManager.currentSessionId, petType.name, contextMessageLimit
        )

        val messages = mutableListOf<Message>()
        messages.add(Message("system", enhancedPrompt))

        recentMessages
            .distinctBy { "${it.role}:${it.content}" }
            .sortedBy { it.timestamp }
            .forEach { messages.add(Message(it.role, it.content)) }

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
            apiService.makeStreamingApiRequest(request).collect { content ->
                listener.onContent(content)
            }
            listener.onComplete()
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

    suspend fun saveChatMessage(message: ChatMessage, petType: PetTypes) {
        val entity = ChatEntity(
            content = message.content,
            petType = petType.name,
            sessionId = sessionManager.currentSessionId,
            role = if (message.isFromUser) "user" else "assistant",
            isImportant = isMessageImportant(message.content)
        )
        chatDao.insert(entity)

        val unprocessedCount = chatDao.getUnprocessedChatsCount()
        if (unprocessedCount > 20) analysisUseCase.summarizeConversation()
    }

    private fun isMessageImportant(content: String): Boolean {
        return content.contains("?") || content.contains("!") ||
                content.length > 50 || content.contains("喜欢") ||
                content.contains("不喜欢") || content.contains("想要")
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

    // region Delegation

    fun consumeLastPictureInfo() = pictureInfoParser.consumeLastPictureInfo()

    suspend fun analyzeChats() = analysisUseCase.analyzeChats()

    // endregion
}
