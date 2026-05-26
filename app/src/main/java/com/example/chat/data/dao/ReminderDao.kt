package com.example.chat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.chat.data.entity.ReminderEntity

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isCompleted = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND scheduledTimeMillis <= :now")
    suspend fun getPendingReminders(now: Long = System.currentTimeMillis()): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?
}
