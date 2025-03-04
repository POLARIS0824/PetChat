package com.example.chat.model

/**
 * API请求的数据类
 */
data class DeepseekRequest(
    val messages: List<Message>,    // 对话消息列表
    val model: String,              // 使用的模型名称
    val temperature: Double,        // 回复的随机性参数
    val max_tokens: Int? = null     // 最大返回长度
)

/**
 * API消息的数据类
 */
data class Message(
    val role: String,    // 消息角色（system/user/assistant）
    val content: String  // 消息内容
)

/**
 * API响应的数据类
 */
data class DeepseekResponse(
    val choices: List<Choice>  // API返回的选项列表
)

/**
 * API响应选项的数据类
 */
data class Choice(
    val message: Message  // 选中的回复消息
)

/**
 * AI分析结果的数据类
 */
data class ChatAnalysisResult(
    val summary: String,
    val preferences: List<String>,
    val patterns: List<String>
)