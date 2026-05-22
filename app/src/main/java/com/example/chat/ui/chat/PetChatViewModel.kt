package com.example.chat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.repository.ChatRepository
import com.example.chat.data.repository.SessionManager
import com.example.chat.model.ChatMessage
import com.example.chat.model.ChatUiState
import com.example.chat.model.PetType
import com.example.chat.model.PictureInfo
import com.example.chat.model.SessionInfo
import com.example.chat.model.StreamResponseListener
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
        sendMessageStreaming(message)
    }

    private fun sendMessageStreaming(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
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

                val responseListener = object : StreamResponseListener {
                    private val responseBuffer = StringBuffer()

                    override fun onContent(content: String) {
                        responseBuffer.append(content)
                        val updatedMessage = petMessage.copy(content = responseBuffer.toString())
                        updateReady { state ->
                            state.copy(
                                streamingMessage = updatedMessage,
                                chatHistory = state.chatHistory.dropLast(1) + updatedMessage,
                                shouldScrollToBottom = false
                            )
                        }
                        scrollJob?.cancel()
                        scrollJob = viewModelScope.launch {
                            delay(50)
                            updateReady { it.copy(shouldScrollToBottom = true) }
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
                                shouldScrollToBottom = false
                            )
                        }

                        viewModelScope.launch {
                            repository.saveChatMessage(finalMessage, petType)
                            val pictureInfo = repository.consumeLastPictureInfo()
                            if (pictureInfo != null) lastPictureInfo = pictureInfo
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
                                chatHistory = it.chatHistory.dropLast(1) + errorMessage
                            )
                        }
                        viewModelScope.launch {
                            repository.saveChatMessage(errorMessage, petType)
                        }
                    }
                }

                repository.getPetResponseWithPictureInfoStreaming(petType, message, responseListener)

            } catch (e: Exception) {
                e.printStackTrace()
                updateReady {
                    it.copy(isStreaming = false, isForegroundLoading = false, streamingMessage = null)
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
