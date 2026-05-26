package com.example.chat.data.tools

import com.example.chat.data.dao.ChatDao
import com.example.chat.data.repository.SessionManager
import com.example.chat.model.PetType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemorySearchTool @Inject constructor(
    private val chatDao: ChatDao,
    private val sessionManager: SessionManager
) : Tool {
    override val name = "search_memory"
    override val displayName = "搜索记忆"
    override val description = "搜索与主人的聊天历史中的重要信息，如偏好、习惯、重要事件等"

    override val parametersJson = """
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "搜索关键词，如'喜欢的食物'、'生日'、'讨厌的东西'"
                },
                "pet_type": {
                    "type": "string",
                    "enum": ["CAT", "DOG", "HAMSTER", "SHIBA"],
                    "description": "可选，限定搜索特定宠物的记忆"
                }
            },
            "required": ["query"]
        }
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(arguments: String): ToolResult {
        return try {
            val args = json.decodeFromString<SearchArgs>(arguments)
            val query = args.query ?: return ToolResult(false, "搜索关键词不能为空", "搜索词为空")

            val sessionId = sessionManager.currentSessionId
            val messages = chatDao.searchMessagesByKeyword(sessionId, query, limit = 15)
                .filter { it.role != "system" }
                .filter { args.pet_type == null || it.petType == args.pet_type }

            if (messages.isEmpty()) {
                return ToolResult(true, "聊天记录中没有找到与'$query'相关的信息", "未找到相关记忆")
            }

            val result = messages.take(5).joinToString("\n") { msg ->
                val petType = PetType.entries.firstOrNull { it.name == msg.petType }
                val petName = petType?.displayName ?: msg.petType
                "[$petName] ${msg.role}: ${msg.content}"
            }

            ToolResult(true, "找到${messages.size}条相关记忆:\n$result", "找到${messages.size}条相关记忆")
        } catch (e: Exception) {
            ToolResult(false, "记忆搜索失败: ${e.message}", "搜索记忆失败")
        }
    }

    @Serializable
    private data class SearchArgs(
        val query: String? = null,
        val pet_type: String? = null
    )
}
