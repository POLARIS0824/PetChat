package com.example.chat.data.repository

data class ApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}
