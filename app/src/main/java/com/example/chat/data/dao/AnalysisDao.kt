package com.example.chat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.chat.data.entity.ChatAnalysisEntity

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM chat_analysis WHERE petType = :petType ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAnalysis(petType: String): ChatAnalysisEntity?

    @Insert
    suspend fun insert(analysis: ChatAnalysisEntity)
}