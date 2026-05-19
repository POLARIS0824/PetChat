package com.example.chat.data.repository

import com.example.chat.data.entity.NoteEntity
import com.example.chat.data.dao.NotesDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val notesDao: NotesDao
) {
    suspend fun getNotesByType(petType: String): List<NoteEntity> =
        notesDao.getByType(petType)

    suspend fun getAllNotes(): List<NoteEntity> =
        notesDao.getAll()

    suspend fun insertNote(note: NoteEntity) =
        notesDao.insert(note)

    suspend fun deleteNote(note: NoteEntity) =
        notesDao.delete(note)

    suspend fun updateNote(note: NoteEntity) =
        notesDao.update(note)
}
