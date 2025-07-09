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
     * 获取指定会话的所有消息
     */
    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId AND petType = :petType ORDER BY timestamp ASC")
    suspend fun getSessionMessages(sessionId: String, petType: String): List<ChatEntity>
    
    /**
     * 获取指定宠物类型的所有消息，不考虑会话ID
     */
    @Query("SELECT * FROM chat_history WHERE petType = :petType ORDER BY timestamp ASC")
    suspend fun getMessagesByPetType(petType: String): List<ChatEntity>

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

    // 新增：获取指定会话的最近消息
    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId AND petType = :petType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessionMessages(sessionId: String, petType: String, limit: Int): List<ChatEntity>
    
    // 新增：获取指定会话的重要消息
    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId AND isImportant = 1 ORDER BY timestamp ASC")
    suspend fun getImportantMessages(sessionId: String): List<ChatEntity>
    
    // 新增：标记消息为重要
    @Query("UPDATE chat_history SET isImportant = :isImportant WHERE id = :messageId")
    suspend fun markMessageImportant(messageId: Long, isImportant: Boolean)

    @Insert
    suspend fun insertNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    // 在 ChatDao.kt 中添加
    @Query("""
    SELECT ch.sessionId, ch.petType, ch.content as lastMessage, MAX(ch.timestamp) as timestamp
    FROM chat_history ch
    GROUP BY ch.sessionId
    ORDER BY timestamp DESC
    """)
    suspend fun getAllSessions(): List<SessionEntity>

    // 添加 SessionEntity 数据类
    @Entity
    data class SessionEntity(
        val sessionId: String,
        val petType: String,
        val lastMessage: String,
        val timestamp: Long
    )
}

