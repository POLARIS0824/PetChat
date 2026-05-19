package com.example.chat.model

import com.example.chat.data.entity.NoteEntity

sealed interface NotesUiState {
    data object Loading : NotesUiState
    data class Ready(
        val notes: List<NoteEntity> = emptyList(),
        val selectedPetType: String? = null
    ) : NotesUiState
}
