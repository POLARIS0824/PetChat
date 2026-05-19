package com.example.chat.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.entity.NoteEntity
import com.example.chat.data.repository.NotesRepository
import com.example.chat.model.NotesUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotesUiState>(NotesUiState.Loading)
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        loadNotes()
    }

    private fun updateReady(transform: (NotesUiState.Ready) -> NotesUiState.Ready) {
        _uiState.update { current ->
            if (current is NotesUiState.Ready) transform(current) else current
        }
    }

    fun loadNotes() {
        viewModelScope.launch {
            val selectedType = (uiState.value as? NotesUiState.Ready)?.selectedPetType
            val notes = when (selectedType) {
                null -> repository.getAllNotes()
                else -> repository.getNotesByType(selectedType)
            }
            _uiState.value = NotesUiState.Ready(
                notes = notes,
                selectedPetType = selectedType
            )
        }
    }

    fun addNote(content: String, petType: String) {
        viewModelScope.launch {
            val note = NoteEntity(
                content = content,
                petType = petType,
                timestamp = System.currentTimeMillis()
            )
            repository.insertNote(note)
            loadNotes()
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
            loadNotes()
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note)
            loadNotes()
        }
    }

    fun setFilter(petType: String?) {
        _uiState.update { current ->
            if (current is NotesUiState.Ready) current.copy(selectedPetType = petType) else current
        }
        loadNotes()
    }
}
