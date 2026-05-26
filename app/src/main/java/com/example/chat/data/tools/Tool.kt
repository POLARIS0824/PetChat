package com.example.chat.data.tools

interface Tool {
    val name: String
    val displayName: String
    val description: String
    val parametersJson: String

    suspend fun execute(arguments: String): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val content: String,
    val displayMessage: String
)
