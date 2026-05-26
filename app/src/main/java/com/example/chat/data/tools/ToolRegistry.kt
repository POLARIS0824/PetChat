package com.example.chat.data.tools

import com.example.chat.model.ApiTool
import com.example.chat.model.FunctionDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    private val tools: Set<@JvmSuppressWildcards Tool>
) {
    private val toolMap: Map<String, Tool> = tools.associateBy { it.name }

    fun getApiTools(): List<ApiTool> {
        val json = Json { ignoreUnknownKeys = true }
        return tools.map { tool ->
            val params = json.decodeFromString<JsonObject>(tool.parametersJson)
            ApiTool(
                function = FunctionDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = params
                )
            )
        }
    }

    suspend fun executeTool(name: String, arguments: String): ToolResult {
        val tool = toolMap[name]
        return if (tool != null) {
            tool.execute(arguments)
        } else {
            ToolResult(
                success = false,
                content = "未知工具: $name",
                displayMessage = "未知工具: $name"
            )
        }
    }

    fun getDisplayName(name: String): String? = toolMap[name]?.displayName
}
