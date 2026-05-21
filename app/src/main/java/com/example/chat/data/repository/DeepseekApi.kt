package com.example.chat.data.repository

import com.example.chat.model.DeepseekRequest
import com.example.chat.model.DeepseekResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

interface DeepseekApi {

    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: DeepseekRequest): DeepseekResponse

    @POST("chat/completions")
    @Streaming
    suspend fun chatCompletionsStreaming(@Body request: DeepseekRequest): ResponseBody
}
