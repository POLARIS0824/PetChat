package com.example.chat.model

sealed interface NotesUiState {
    data object Loading : NotesUiState
    data class Ready(
        val notes: List<NoteUiModel> = emptyList(),
        val selectedPetType: String? = null
    ) : NotesUiState
    data class Error(val message: String) : NotesUiState
}

data class NoteUiModel(
    val id: Long,
    val content: String,
    val petType: PetType,
    val timestamp: Long
)
