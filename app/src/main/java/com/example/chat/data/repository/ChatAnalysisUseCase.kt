package com.example.chat.data.repository

import android.util.Log
import com.example.chat.data.dao.AnalysisDao
import com.example.chat.data.entity.ChatAnalysisEntity
import com.example.chat.data.dao.ChatDao
import com.example.chat.data.entity.ChatEntity
import com.example.chat.model.ChatAnalysisResult
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.Message
import com.example.chat.model.PetTypes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatAnalysisUseCase @Inject constructor(
    private val chatDao: ChatDao,
    private val analysisDao: AnalysisDao,
    private val apiService: ChatApiService,
    private val promptBuilder: PromptBuilder,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val model = com.example.chat.BuildConfig.PETCHAT_MODEL.trim().ifBlank { "deepseek-v3" }

    suspend fun analyzeChats() {
        val unprocessedChats = chatDao.getUnprocessedChats()
        if (unprocessedChats.size < 10) return

        val chatsByPetType = unprocessedChats.groupBy { it.petType }

        for ((petTypeString, chats) in chatsByPetType) {
            if (chats.size < 10) continue

            val analysisPrompt = """
                请分析以下聊天记录，并提供:
                1. 对话总结
                2. 用户偏好和兴趣
                3. 主要互动模式

                聊天记录：
                ${chats.joinToString("\n") {
                    if (it.isFromUser) "用户: ${it.content}" else "宠物: ${it.content}"
                }}

                请用JSON格式返回，格式如下：
                {
                    "summary": "对话总结",
                    "preferences": ["偏好1", "偏好2"],
                    "patterns": ["互动模式1", "互动模式2"]
                }
            """.trimIndent()

            val request = DeepseekRequest(
                model = model,
                messages = listOf(
                    Message("system", "我是一个聊天分析助手，可以帮你分析聊天记录。"),
                    Message("user", analysisPrompt)
                )
            )

            try {
                val response = apiService.makeApiRequest(request)
                val analysisText = response.choices.firstOrNull()?.message?.content ?: continue
                val analysis = json.decodeFromString<ChatAnalysisResult>(analysisText)

                val analysisEntity = ChatAnalysisEntity(
                    petType = petTypeString,
                    summary = analysis.summary,
                    preferences = json.encodeToString(analysis.preferences),
                    patterns = json.encodeToString(analysis.patterns)
                )
                analysisDao.insert(analysisEntity)
                chatDao.update(chats.map { it.copy(isProcessed = true) })
            } catch (e: Exception) {
                Log.e("ANALYSIS", "分析聊天记录出错 (petType=$petTypeString)", e)
            }
        }
    }

    suspend fun summarizeConversation() {
        val messages = chatDao.getUnprocessedChats()
        if (messages.size < 10) return

        val summaryPrompt = """
            请对以下对话进行摘要，提取关键信息，不超过100字：
            ${messages.joinToString("\n") {
                (if (it.isFromUser) "用户: " else "宠物: ") + it.content
            }}
        """.trimIndent()

        try {
            val petType = PetTypes.entries.firstOrNull { it.name == messages.first().petType } ?: PetTypes.CAT
            val summary = getPetResponse(petType, summaryPrompt)
            val summaryEntity = ChatEntity(
                content = "【对话摘要】$summary",
                isFromUser = false,
                petType = messages.first().petType,
                sessionId = messages.first().sessionId,
                role = "system",
                isImportant = true,
                isProcessed = true,
                isSummary = true
            )
            chatDao.insert(summaryEntity)
            chatDao.update(messages.map { it.copy(isProcessed = true) })
        } catch (e: Exception) {
            Log.e("SUMMARY", "对话摘要出错", e)
        }
    }

    private suspend fun getPetResponse(petType: PetTypes, userMessage: String): String {
        return try {
            val messages = buildMessages(petType, userMessage)
            val request = DeepseekRequest(model = model, messages = messages)
            val response = apiService.makeApiRequest(request)
            response.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("AI响应为空")
        } catch (e: Exception) {
            Log.e("PET_RESPONSE", "获取宠物回复出错", e)
            "抱歉，我现在有点累了，待会再聊吧。"
        }
    }

    private suspend fun buildMessages(petType: PetTypes, userMessage: String): List<Message> {
        val enhancedPrompt = promptBuilder.build(petType)
        return listOf(
            Message("system", enhancedPrompt),
            Message("user", userMessage)
        )
    }
}
