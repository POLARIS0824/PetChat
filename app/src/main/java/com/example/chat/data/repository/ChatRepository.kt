package com.example.chat.data.repository

import android.util.Log
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatEntity
import com.example.chat.data.tools.ToolRegistry
import com.example.chat.data.tools.ToolResult
import com.example.chat.model.ChatMessage
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.Message
import com.example.chat.model.PetType
import com.example.chat.model.StreamEvent
import com.example.chat.model.StreamResponseListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface AgentStreamListener {
    fun onContent(content: String)
    fun onThinking()
    fun onToolCallStart(toolCallId: String, toolName: String, displayName: String)
    fun onToolCallComplete(toolCallId: String, toolName: String, displayName: String, result: ToolResult)
    fun onComplete()
    fun onError(e: Exception)
}

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val apiService: ChatApiService,
    private val sessionManager: SessionManager,
    private val promptBuilder: PromptBuilder,
    private val pictureInfoParser: PictureInfoParser,
    private val analysisUseCase: ChatAnalysisUseCase,
    private val settingsManager: SettingsManager,
    private val toolRegistry: ToolRegistry,
) {

    companion object {
        private const val CONTEXT_MESSAGE_LIMIT = 3
        private const val AGENT_CONTEXT_LIMIT = 15
        private const val SUMMARY_THRESHOLD = 20
        private const val IMPORTANT_MESSAGE_LENGTH = 50
        private const val MAX_AGENT_ITERATIONS = 5
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

    // region Agent Loop

    private suspend fun buildAgentMessages(petType: PetType, userMessage: String): List<Message> {
        val enhancedPrompt = buildAgentSystemPrompt(petType)
        val recentMessages = chatDao.getRecentSessionMessages(
            sessionManager.currentSessionId, petType.name, AGENT_CONTEXT_LIMIT
        )

        val messages = mutableListOf<Message>()
        messages.add(Message("system", enhancedPrompt))

        recentMessages
            .filter { it.content.isNotBlank() }
            .distinctBy { "${it.role}:${it.content}" }
            .sortedBy { it.timestamp }
            .forEach { messages.add(Message(it.role, it.content)) }

        messages.add(Message("user", userMessage))
        return messages
    }

    private suspend fun buildAgentSystemPrompt(petType: PetType): String {
        val basePrompt = promptBuilder.build(petType)
        return """
            $basePrompt

            你有能力使用工具来帮助主人。可用的工具包括管理笔记、设置提醒和搜索聊天记忆。
            当主人需要你执行具体操作时（如记笔记、设提醒、查找信息），请调用相应的工具。
            调用工具后，根据工具返回的结果继续回复主人。
            如果不需要使用工具，直接回复主人即可。
        """.trimIndent()
    }

    suspend fun getPetAgentResponse(
        petType: PetType,
        userMessage: String,
        listener: AgentStreamListener
    ) {
        try {
            val config = settingsManager.getConfig()
            val effectiveModel = config.model.trim().takeIf { it.isNotBlank() }
                ?: SettingsManager.DEFAULT_MODEL

            val messages = buildAgentMessages(petType, userMessage).toMutableList()
            val tools = toolRegistry.getApiTools()

            var iteration = 0

            while (iteration < MAX_AGENT_ITERATIONS) {
                val request = DeepseekRequest(
                    model = effectiveModel,
                    messages = messages,
                    stream = true,
                    tools = tools,
                    tool_choice = "auto"
                )

                val accumulator = ToolCallAccumulator()
                val contentBuffer = StringBuilder()
                var hasContent = false

                apiService.makeAgentStreamingRequest(request).collect { event ->
                    when (event) {
                        is StreamEvent.Content -> {
                            hasContent = true
                            contentBuffer.append(event.text)
                            listener.onContent(event.text)
                        }
                        is StreamEvent.ToolCallDeltaEvent -> {
                            accumulator.apply(event)
                        }
                        is StreamEvent.StreamFinished -> { /* stream ended */ }
                    }
                }

                if (accumulator.hasPendingCalls()) {
                    val toolCalls = accumulator.toToolCalls()

                    // Add assistant message with content + tool_calls
                    val assistantContent = contentBuffer.toString().takeIf { it.isNotBlank() }
                    messages.add(Message(
                        role = "assistant",
                        content = assistantContent,
                        tool_calls = toolCalls
                    ))

                    // Execute each tool
                    for (toolCall in toolCalls) {
                        val func = toolCall.function ?: continue
                        val toolName = func.name ?: continue
                        val toolCallId = toolCall.id ?: ""
                        val displayName = toolRegistry.getDisplayName(toolName) ?: toolName

                        listener.onToolCallStart(toolCallId, toolName, displayName)
                        val result = withContext(Dispatchers.IO) {
                            toolRegistry.executeTool(toolName, func.arguments ?: "{}")
                        }
                        listener.onToolCallComplete(toolCallId, toolName, displayName, result)

                        // Add tool result message
                        messages.add(Message(
                            role = "tool",
                            tool_call_id = toolCallId,
                            name = toolName,
                            content = result.content
                        ))
                    }

                    listener.onThinking()
                    iteration++
                } else {
                    // Final response - no tool calls
                    val finalContent = contentBuffer.toString()
                    if (finalContent.isNotBlank() || hasContent) {
                        messages.add(Message("assistant", finalContent))
                    }
                    listener.onComplete()
                    return
                }
            }

            // Max iterations reached, force final response without tools
            messages.add(Message(
                "system",
                "你已经达到了工具调用的最大次数限制。请根据已有的信息直接回复主人，不要再调用工具。"
            ))
            val finalMessages = messages
            val finalRequest = DeepseekRequest(
                model = effectiveModel,
                messages = finalMessages,
                stream = true,
                tools = null
            )
            apiService.makeStreamingApiRequest(finalRequest).collect { content ->
                listener.onContent(content)
            }
            listener.onComplete()
        } catch (e: Exception) {
            listener.onError(e)
        }
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
