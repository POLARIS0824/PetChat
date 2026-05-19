package com.example.chat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DeepseekRequest(
    val model: String = "deepseek-v3",
    val messages: List<Message>,
    val stream: Boolean = false,
)

@Serializable
data class Message(
    val role: String = "user",
    val content: String,
)

@Serializable
data class DeepseekResponse(
    val choices: List<Choice>,
    @SerialName("object") val objectType: String? = null,
    val usage: Usage? = null,
    val created: Long? = null,
    val system_fingerprint: String? = null,
    val model: String? = null,
    val id: String? = null,
) {
    @Serializable
    data class Choice(
        val message: Message = Message(content = ""),
        val finish_reason: String? = null,
        val index: Int? = null,
        val logprobs: JsonElement? = null,
        val delta: Delta? = null
    )

    @Serializable
    data class Delta(
        val content: String? = null,
        val role: String? = null
    )

    @Serializable
    data class Usage(
        val prompt_tokens: Int? = null,
        val completion_tokens: Int? = null,
        val total_tokens: Int? = null
    )
}

interface StreamResponseListener {
    fun onContent(content: String)
    fun onComplete()
    fun onError(e: Exception)
}

@Serializable
data class ChatAnalysisResult(
    val summary: String,
    val preferences: List<String>,
    val patterns: List<String>
)
