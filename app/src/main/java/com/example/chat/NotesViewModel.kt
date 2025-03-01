package com.example.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.ChatDatabase
import com.example.chat.data.NoteEntity
import com.example.chat.model.PetTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ChatDatabase.getDatabase(application).chatDao()
    
    // 当前选中的宠物类型过滤器
    var selectedPetType by mutableStateOf<String?>(null)
        private set
    
    // 便利贴列表
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes = _notes.asStateFlow()

    // 初始加载所有便利贴
    init {
        loadNotes()
    }

    // 加载便利贴
    fun loadNotes() {
        viewModelScope.launch {
            _notes.value = when (selectedPetType) {
                null -> dao.getNotesByType(PetTypes.CAT.name) + dao.getNotesByType(PetTypes.DOG.name)
                else -> dao.getNotesByType(selectedPetType!!)
            }
        }
    }

    // 添加便利贴
    fun addNote(content: String, petType: PetTypes) {
        viewModelScope.launch {
            dao.insertNote(NoteEntity(content = content, petType = petType.name))
            loadNotes()
        }
    }

    // 删除便利贴
    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            dao.deleteNote(note)
            loadNotes()
        }
    }

    // 更新便利贴
    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            dao.updateNote(note)
            loadNotes()
        }
    }

    // 设置过滤器
    fun setFilter(petType: String?) {
        selectedPetType = petType
        loadNotes()
    }
} 