package com.example.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.ChatDao
import com.example.chat.data.NoteEntity
import com.example.chat.model.NotesUiState
import com.example.chat.model.PetTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val chatDao: ChatDao
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
                null -> PetTypes.entries.flatMap { chatDao.getNotesByType(it.name) }
                else -> chatDao.getNotesByType(selectedType)
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
            chatDao.insertNote(note)
            loadNotes()
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            chatDao.deleteNote(note)
            loadNotes()
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            chatDao.updateNote(note)
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
