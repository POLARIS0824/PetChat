package com.example.chat.data

import androidx.room.*

/**
 * 聊天记录数据访问对象（DAO）
 * 定义了所有与聊天记录相关的数据库操作
 */
@Dao
interface ChatDao {
    /**
     * 获取所有未处理的聊天记录
     * 按时间戳升序排列，确保按照对话发生的顺序处理
     */
    @Query("SELECT * FROM chat_history WHERE isProcessed = 0 ORDER BY timestamp ASC")
    suspend fun getUnprocessedChats(): List<ChatEntity>

    /**
     * 获取未处理的聊天记录数量
     * 用于判断是否达到需要处理的阈值（10条）
     */
    @Query("SELECT COUNT(*) FROM chat_history WHERE isProcessed = 0")
    suspend fun getUnprocessedChatsCount(): Int

    /**
     * 插入新的聊天记录
     */
    @Insert
    suspend fun insert(chat: ChatEntity)

    /**
     * 批量更新聊天记录
     * 主要用于将消息标记为已处理
     */
    @Update
    suspend fun update(chats: List<ChatEntity>)

    /**
     * 删除指定时间戳之前的已处理聊天记录
     * 用于清理旧的聊天记录，避免数据库无限增长
     */
    @Query("DELETE FROM chat_history WHERE isProcessed = 1 AND timestamp < :timestamp")
    suspend fun deleteOldProcessedChats(timestamp: Long)

    @Query("SELECT * FROM chat_analysis WHERE petType = :petType ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAnalysis(petType: String): ChatAnalysisEntity?

    @Insert
    suspend fun insertAnalysis(analysis: ChatAnalysisEntity)

    @Query("SELECT * FROM notes WHERE petType = :petType ORDER BY timestamp DESC")
    suspend fun getNotesByType(petType: String): List<NoteEntity>

    @Insert
    suspend fun insertNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)
} 