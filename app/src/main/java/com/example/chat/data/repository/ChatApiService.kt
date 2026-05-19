package com.example.chat.data.repository

import android.util.Log
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.DeepseekResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatApiService @Inject constructor(
    private val deepseekApi: DeepseekApi,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun makeApiRequest(request: DeepseekRequest): DeepseekResponse {
        return try {
            deepseekApi.chatCompletions(request)
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            throw IOException("API请求失败: ${e.code()} $errorBody", e)
        }
    }

    fun makeStreamingApiRequest(request: DeepseekRequest): Flow<String> = callbackFlow {
        val streamingRequest = request.copy(stream = true)
        val responseBody: ResponseBody = deepseekApi.chatCompletionsStreaming(streamingRequest)
        try {
            val source = responseBody.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) continue
                if (line.startsWith("data: ")) {
                    val jsonData = line.substring(6)
                    if (jsonData == "[DONE]") {
                        close()
                        return@callbackFlow
                    }
                    try {
                        val chunkResponse = json.decodeFromString<DeepseekResponse>(jsonData)
                        val content = chunkResponse.choices.firstOrNull()?.delta?.content
                        if (content != null) {
                            trySend(content)
                        }
                    } catch (e: Exception) {
                        Log.e("API_STREAM_ERROR", "解析流式数据出错: ${e.message}", e)
                    }
                }
            }
            close()
        } catch (e: Exception) {
            close(e)
        } finally {
            responseBody.close()
        }

        awaitClose()
    }
}
