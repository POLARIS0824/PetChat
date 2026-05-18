package com.example.chat.data.repository

import com.example.chat.data.ChatDao
import com.example.chat.data.NoteEntity
import com.example.chat.model.PetTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    suspend fun getNotesByType(petType: String): List<NoteEntity> =
        chatDao.getNotesByType(petType)

    suspend fun getAllNotes(): List<NoteEntity> =
        PetTypes.entries.flatMap { chatDao.getNotesByType(it.name) }

    suspend fun insertNote(note: NoteEntity) =
        chatDao.insertNote(note)

    suspend fun deleteNote(note: NoteEntity) =
        chatDao.deleteNote(note)

    suspend fun updateNote(note: NoteEntity) =
        chatDao.updateNote(note)
}
