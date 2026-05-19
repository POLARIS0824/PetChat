package com.example.chat.data.repository

import com.example.chat.data.entity.NoteEntity
import com.example.chat.data.dao.NotesDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val notesDao: NotesDao
) {
    suspend fun getNotesByType(petType: String): List<NoteEntity> =
        notesDao.getByType(petType)

    fun getNotesByTypeFlow(petType: String): Flow<List<NoteEntity>> =
        notesDao.getByTypeFlow(petType)

    suspend fun getAllNotes(): List<NoteEntity> =
        notesDao.getAll()

    fun getAllNotesFlow(): Flow<List<NoteEntity>> =
        notesDao.getAllFlow()

    suspend fun insertNote(note: NoteEntity) =
        notesDao.insert(note)

    suspend fun deleteNote(note: NoteEntity) =
        notesDao.delete(note)

    suspend fun updateNote(note: NoteEntity) =
        notesDao.update(note)
}
