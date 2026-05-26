package com.example.chat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.repository.AgentStreamListener
import com.example.chat.data.repository.ChatRepository
import com.example.chat.data.repository.SessionManager
import com.example.chat.data.tools.ToolResult
import com.example.chat.model.AgentStatus
import com.example.chat.model.ChatMessage
import com.example.chat.model.ChatUiState
import com.example.chat.model.PetType
import com.example.chat.model.PictureInfo
import com.example.chat.model.SessionInfo
import com.example.chat.model.ToolCallInfo
import com.example.chat.model.ToolStatus
import android.app.Application
import com.example.chat.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PetChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val sessionManager: SessionManager,
    private val application: Application,
) : ViewModel() {

    private val _chatUiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private var lastPictureInfo: PictureInfo? = null
    private var scrollJob: Job? = null
    private var streamingJob: Job? = null

    private val _allSessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val allSessions: StateFlow<List<SessionInfo>> = _allSessions.asStateFlow()

    init {
        loadChatHistory()
    }

    private fun readyState(): ChatUiState.Ready {
        val state = _chatUiState.value
        return if (state is ChatUiState.Ready) state else ChatUiState.Ready()
    }

    private fun updateReady(transform: (ChatUiState.Ready) -> ChatUiState.Ready) {
        _chatUiState.update { current ->
            if (current is ChatUiState.Ready) transform(current) else current
        }
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            val currentType = readyState().currentPetType
            val messages = sessionManager.getSessionMessages(
                sessionManager.currentSessionId, currentType
            )
            val chatMessages = messages.map { entity ->
                ChatMessage(
                    content = entity.content,
                    role = entity.role,
                    petType = PetType.entries.firstOrNull { it.name == entity.petType } ?: PetType.CAT,
                    timestamp = entity.timestamp,
                    id = "db_${entity.id}"
                )
            }
            _chatUiState.value = ChatUiState.Ready(
                chatHistory = chatMessages,
                currentPetType = currentType,
                shouldScrollToBottom = chatMessages.isNotEmpty()
            )
            if (chatMessages.isNotEmpty()) {
                delay(100)
                updateReady { it.copy(shouldScrollToBottom = false) }
                updateReady { it.copy(shouldScrollToBottom = true) }
            }
        }
    }

    fun selectPetType(petType: PetType) {
        streamingJob?.cancel()
        updateReady { it.copy(currentPetType = petType) }
        loadChatHistory()
    }

    fun createNewSession() {
        sessionManager.createNewSession()
        updateReady { it.copy(chatHistory = emptyList()) }
        loadAllSessions()
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        if (readyState().isStreaming) return
        sendMessageAgent(message)
    }

    private fun sendMessageAgent(message: String) {
        if (message.isBlank()) return

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            val petType = readyState().currentPetType

            updateReady {
                it.copy(
                    isForegroundLoading = true,
                    isStreaming = true
                )
            }

            try {
                val userMessage = ChatMessage(
                    content = message,
                    role = "user",
                    petType = petType
                )
                updateReady { it.copy(chatHistory = it.chatHistory + userMessage) }
                repository.saveChatMessage(userMessage, petType)

                val petMessage = ChatMessage(
                    content = "",
                    role = "assistant",
                    petType = petType
                )
                updateReady {
                    it.copy(
                        streamingMessage = petMessage,
                        chatHistory = it.chatHistory + petMessage,
                        shouldScrollToBottom = false
                    )
                }
                delay(50)
                updateReady { it.copy(shouldScrollToBottom = true) }

                val responseListener = object : AgentStreamListener {
                    private val responseBuffer = StringBuffer()

                    override fun onContent(content: String) {
                        responseBuffer.append(content)
                        val updatedMessage = petMessage.copy(content = responseBuffer.toString())
                        updateReady { state ->
                            state.copy(
                                streamingMessage = updatedMessage,
                                chatHistory = state.chatHistory.map {
                                    if (it.id == petMessage.id) updatedMessage else it
                                },
                                shouldScrollToBottom = false
                            )
                        }
                        scrollJob?.cancel()
                        scrollJob = viewModelScope.launch {
                            delay(50)
                            updateReady { it.copy(shouldScrollToBottom = true) }
                        }
                    }

                    override fun onThinking() {
                        updateReady { it.copy(agentStatus = AgentStatus.THINKING) }
                    }

                    override fun onToolCallStart(
                        toolCallId: String,
                        toolName: String,
                        displayName: String
                    ) {
                        updateReady { it.copy(agentStatus = AgentStatus.EXECUTING) }
                        val statusMsg = ChatMessage(
                            content = "",
                            role = "tool_status",
                            petType = petType,
                            id = toolCallId.ifEmpty { java.util.UUID.randomUUID().toString() },
                            toolCallInfo = ToolCallInfo(
                                toolName = toolName,
                                displayName = displayName,
                                status = ToolStatus.EXECUTING
                            )
                        )
                        updateReady {
                            it.copy(chatHistory = it.chatHistory + statusMsg)
                        }
                    }

                    override fun onToolCallComplete(
                        toolCallId: String,
                        toolName: String,
                        displayName: String,
                        result: ToolResult
                    ) {
                        updateReady { it.copy(agentStatus = null) }
                        val status = if (result.success) ToolStatus.COMPLETED else ToolStatus.FAILED
                        val statusMsg = ChatMessage(
                            content = result.displayMessage,
                            role = "tool_status",
                            petType = petType,
                            id = toolCallId.ifEmpty { java.util.UUID.randomUUID().toString() },
                            toolCallInfo = ToolCallInfo(
                                toolName = toolName,
                                displayName = displayName,
                                status = status,
                                resultPreview = result.displayMessage
                            )
                        )
                        updateReady {
                            it.copy(
                                chatHistory = it.chatHistory.map { msg ->
                                    if (msg.id == toolCallId && msg.role == "tool_status" && msg.toolCallInfo?.status == ToolStatus.EXECUTING)
                                        statusMsg
                                    else msg
                                }
                            )
                        }
                    }

                    override fun onComplete() {
                        val finalContent = responseBuffer.toString()
                        val finalMessage = petMessage.copy(content = finalContent)

                        updateReady {
                            it.copy(
                                isStreaming = false,
                                streamingMessage = null,
                                isForegroundLoading = false,
                                agentStatus = null,
                                shouldScrollToBottom = false
                            )
                        }

                        viewModelScope.launch {
                            repository.saveChatMessage(finalMessage, petType)
                            val unprocessedCount = repository.getUnprocessedChatsCount()
                            if (unprocessedCount >= 10) repository.analyzeChats()
                        }

                        viewModelScope.launch {
                            delay(100)
                            updateReady { it.copy(shouldScrollToBottom = true) }
                        }
                    }

                    override fun onError(e: Exception) {
                        e.printStackTrace()
                        val errorMessage = petMessage.copy(
                            content = application.getString(R.string.chat_error_fallback)
                        )
                        updateReady {
                            it.copy(
                                isStreaming = false,
                                streamingMessage = null,
                                isForegroundLoading = false,
                                agentStatus = null,
                                chatHistory = it.chatHistory.map { msg ->
                                    if (msg.id == petMessage.id) errorMessage else msg
                                }
                            )
                        }
                        viewModelScope.launch {
                            repository.saveChatMessage(errorMessage, petType)
                        }
                    }
                }

                repository.getPetAgentResponse(petType, message, responseListener)

            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                updateReady {
                    it.copy(
                        isStreaming = false,
                        isForegroundLoading = false,
                        streamingMessage = null,
                        agentStatus = null
                    )
                }
            }
        }
    }

    fun resetScroll() {
        viewModelScope.launch {
            updateReady { it.copy(shouldScrollToBottom = false) }
            delay(100)
            updateReady { it.copy(shouldScrollToBottom = true) }
        }
    }

    fun consumeLastPictureInfo(): PictureInfo? {
        val info = lastPictureInfo
        lastPictureInfo = null
        return info
    }

    fun getChatHistory(petType: PetType): List<ChatMessage> {
        val state = readyState()
        return if (state.currentPetType == petType) {
            state.chatHistory
        } else {
            emptyList()
        }
    }

    fun loadAllSessions() {
        viewModelScope.launch {
            _allSessions.value = sessionManager.getAllSessions()
        }
    }

    fun switchToSession(sessionId: String) {
        viewModelScope.launch {
            val session = _allSessions.value.find { it.sessionId == sessionId } ?: return@launch
            sessionManager.setCurrentSessionId(sessionId)
            selectPetType(session.petType)
        }
    }
}
