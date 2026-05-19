package com.example.chat.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.entity.NoteEntity
import com.example.chat.data.repository.NotesRepository
import com.example.chat.model.NoteUiModel
import com.example.chat.model.NotesUiState
import com.example.chat.model.PetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository
) : ViewModel() {

    private val _selectedPetType = MutableStateFlow<String?>(null)

    private val notesFlow = _selectedPetType.flatMapLatest { petType ->
        if (petType == null) repository.getAllNotesFlow()
        else repository.getNotesByTypeFlow(petType)
    }.map { notes -> notes.map { it.toUiModel() } }

    val uiState: StateFlow<NotesUiState> = combine(
        _selectedPetType,
        notesFlow
    ) { selectedType, notes ->
        NotesUiState.Ready(
            notes = notes,
            selectedPetType = selectedType
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesUiState.Loading
    )

    fun addNote(content: String, petType: String) {
        viewModelScope.launch {
            val note = NoteEntity(
                content = content,
                petType = petType,
                timestamp = System.currentTimeMillis()
            )
            repository.insertNote(note)
        }
    }

    fun deleteNote(note: NoteUiModel) {
        viewModelScope.launch {
            repository.deleteNote(note.toEntity())
        }
    }

    fun updateNote(note: NoteUiModel) {
        viewModelScope.launch {
            repository.updateNote(note.toEntity())
        }
    }

    fun setFilter(petType: String?) {
        _selectedPetType.value = petType
    }

    private fun NoteEntity.toUiModel() = NoteUiModel(
        id = id,
        content = content,
        petType = PetType.entries.firstOrNull { it.name == petType } ?: PetType.CAT,
        timestamp = timestamp
    )

    private fun NoteUiModel.toEntity() = NoteEntity(
        id = id,
        content = content,
        petType = petType.name,
        timestamp = timestamp
    )
}
