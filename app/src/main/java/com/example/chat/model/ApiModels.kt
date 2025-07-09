package com.example.chat.model

import com.google.gson.annotations.SerializedName

/**
 * API请求的数据类
 */
data class DeepseekRequest(
    val model: String = "deepseek-r1",
    val messages: List<Message>,
    val stream: Boolean = false,  // 添加stream参数，用于控制是否使用流式传输
//    val temperature: Double = 0.7,
//    val max_tokens: Int = 1024
)

data class Message(
    val role: String = "deepseek-r1",
    val content: String,
//    val reasoning_content : String? = null
)

/**
 * API响应的数据类
 */
data class DeepseekResponse(
    val choices: List<Choice>,
    @SerializedName("object") val object_field: String? = null,
    val usage: Usage? = null,
    val created: Long? = null,
    val system_fingerprint: String? = null,
    val model: String? = null,
    val id: String? = null,
) {
    data class Choice(
        val message: Message,
        val finish_reason: String? = null,
        val index: Int? = null,
        val logprobs: Any? = null,
        // 添加delta字段，用于流式传输时接收部分内容
        val delta: Delta? = null
    )
    
    data class Delta(
        val content: String? = null,
        val role: String? = null
    )
    
    data class Usage(
        val prompt_tokens: Int? = null,
        val completion_tokens: Int? = null,
        val total_tokens: Int? = null
    )
}

/**
 * 流式响应监听器接口
 */
interface StreamResponseListener {
    fun onContent(content: String)
    fun onComplete()
    fun onError(e: Exception)
}

/**
 * AI分析结果的数据类
 */
data class ChatAnalysisResult(
    val summary: String,
    val preferences: List<String>,
    val patterns: List<String>
)