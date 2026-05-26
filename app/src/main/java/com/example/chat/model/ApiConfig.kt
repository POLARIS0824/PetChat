package com.example.chat.model

data class ApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}