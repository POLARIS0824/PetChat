package com.example.chat.model

sealed interface ChatUiState {
    data object Loading : ChatUiState
    data class Ready(
        val chatHistory: List<ChatMessage> = emptyList(),
        val isStreaming: Boolean = false,
        val streamingMessage: ChatMessage? = null,
        val isForegroundLoading: Boolean = false,
        val shouldScrollToBottom: Boolean = false,
        val currentPetType: PetType = PetType.CAT,
        val agentStatus: AgentStatus? = null,
    ) : ChatUiState
    data class Error(val message: String) : ChatUiState
}

enum class AgentStatus { THINKING, EXECUTING }
