package com.example.chat.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.chat.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes WHERE petType = :petType ORDER BY timestamp DESC")
    suspend fun getByType(petType: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE petType = :petType ORDER BY timestamp DESC")
    fun getByTypeFlow(petType: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)
}