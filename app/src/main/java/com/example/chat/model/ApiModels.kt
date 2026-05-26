package com.example.chat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// region Request Models

@Serializable
data class DeepseekRequest(
    val model: String = "",
    val messages: List<Message>,
    val stream: Boolean = false,
    val tools: List<ApiTool>? = null,
    val tool_choice: String? = null,
)

@Serializable
data class Message(
    val role: String,
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null,
    val name: String? = null,
)

@Serializable
data class ApiTool(
    val type: String = "function",
    val function: FunctionDefinition,
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, JsonElement>,
)

// endregion

// region Response Models

@Serializable
data class DeepseekResponse(
    val choices: List<Choice>? = null,
    @SerialName("object") val objectType: String? = null,
    val usage: Usage? = null,
    val created: Long? = null,
    val system_fingerprint: String? = null,
    val model: String? = null,
    val id: String? = null,
) {
    @Serializable
    data class Choice(
        val message: Message? = null,
        val finish_reason: String? = null,
        val index: Int? = null,
        val logprobs: JsonElement? = null,
        val delta: Delta? = null
    )

    @Serializable
    data class Delta(
        val content: String? = null,
        val role: String? = null,
        val tool_calls: List<ToolCallDelta>? = null
    )

    @Serializable
    data class Usage(
        val prompt_tokens: Int? = null,
        val completion_tokens: Int? = null,
        val total_tokens: Int? = null
    )
}

@Serializable
data class ToolCall(
    val id: String? = null,
    val type: String = "function",
    val function: FunctionCall? = null
)

@Serializable
data class FunctionCall(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class ToolCallDelta(
    val index: Int? = null,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCallDelta? = null
)

@Serializable
data class FunctionCallDelta(
    val name: String? = null,
    val arguments: String? = null
)

// endregion

// region Streaming

sealed class StreamEvent {
    data class Content(val text: String) : StreamEvent()
    data class ToolCallDeltaEvent(
        val index: Int,
        val id: String?,
        val functionName: String?,
        val argumentsDelta: String?
    ) : StreamEvent()
    data object StreamFinished : StreamEvent()
}

interface StreamResponseListener {
    fun onContent(content: String)
    fun onComplete()
    fun onError(e: Exception)
}

// endregion

@Serializable
data class ChatAnalysisResult(
    val summary: String,
    val preferences: List<String>,
    val patterns: List<String>
)
