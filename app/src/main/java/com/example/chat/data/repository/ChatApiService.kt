package com.example.chat.data.repository

import android.util.Log
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.DeepseekResponse
import com.example.chat.model.StreamEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        var responseBodyRef: ResponseBody? = null

        withContext(Dispatchers.IO) {
            val streamingRequest = request.copy(stream = true)
            val responseBody: ResponseBody = try {
                deepseekApi.chatCompletionsStreaming(streamingRequest)
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("API_STREAM_ERROR", "Streaming API request failed: ${e.code()} $errorBody", e)
                close(IOException("API请求失败: ${e.code()} $errorBody", e))
                return@withContext
            }

            responseBodyRef = responseBody

            try {
                val source = responseBody.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) continue
                    if (line.startsWith("data:")) {
                        val jsonData = line.substring(5).trim()
                        if (jsonData == "[DONE]") {
                            close()
                            return@withContext
                        }
                        try {
                            val chunkResponse = json.decodeFromString<DeepseekResponse>(jsonData)
                            val content = chunkResponse.choices?.firstOrNull()?.delta?.content
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
                try {
                    responseBody.close()
                } catch (e: Exception) {
                    // Ignore double close exceptions
                }
            }
        }

        awaitClose {
            val body = responseBodyRef
            if (body != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        body.close()
                    } catch (e: Exception) {
                        Log.e("API_STREAM_ERROR", "Error closing response body in awaitClose", e)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun makeAgentStreamingRequest(request: DeepseekRequest): Flow<StreamEvent> = callbackFlow {
        var responseBodyRef: ResponseBody? = null

        withContext(Dispatchers.IO) {
            val streamingRequest = request.copy(stream = true)
            val responseBody: ResponseBody = try {
                deepseekApi.chatCompletionsStreaming(streamingRequest)
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("API_AGENT_ERROR", "Agent streaming request failed: ${e.code()} $errorBody", e)
                close(IOException("API请求失败: ${e.code()} $errorBody", e))
                return@withContext
            }

            responseBodyRef = responseBody

            try {
                val source = responseBody.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) continue
                    if (line.startsWith("data:")) {
                        val jsonData = line.substring(5).trim()
                        if (jsonData == "[DONE]") {
                            trySend(StreamEvent.StreamFinished)
                            close()
                            return@withContext
                        }
                        try {
                            val chunkResponse = json.decodeFromString<DeepseekResponse>(jsonData)
                            val delta = chunkResponse.choices?.firstOrNull()?.delta ?: continue

                            val content = delta.content
                            if (!content.isNullOrEmpty()) {
                                trySend(StreamEvent.Content(content))
                            }

                            val toolCalls = delta.tool_calls
                            if (toolCalls != null) {
                                for (tc in toolCalls) {
                                    trySend(StreamEvent.ToolCallDeltaEvent(
                                        index = tc.index ?: 0,
                                        id = tc.id,
                                        functionName = tc.function?.name,
                                        argumentsDelta = tc.function?.arguments
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("API_AGENT_ERROR", "解析流式数据出错: ${e.message}", e)
                        }
                    }
                }
                trySend(StreamEvent.StreamFinished)
                close()
            } catch (e: Exception) {
                close(e)
            } finally {
                try {
                    responseBody.close()
                } catch (e: Exception) {
                    // Ignore double close exceptions
                }
            }
        }

        awaitClose {
            val body = responseBodyRef
            if (body != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        body.close()
                    } catch (e: Exception) {
                        Log.e("API_AGENT_ERROR", "Error closing response body in awaitClose", e)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
