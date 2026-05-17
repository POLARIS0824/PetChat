package com.example.chat.data.repository

import android.util.Log
import com.example.chat.BuildConfig
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.DeepseekResponse
import com.example.chat.model.StreamResponseListener
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ChatApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val apiKey = BuildConfig.PETCHAT_API_KEY.trim()
    private val baseUrl = BuildConfig.PETCHAT_BASE_URL.trim().trimEnd('/')

    private fun requireApiKey(): String {
        if (apiKey.isBlank()) {
            throw IOException("Missing API key. Configure petchat.apiKey in local.properties or env.")
        }
        return apiKey
    }

    suspend fun makeApiRequest(request: DeepseekRequest): DeepseekResponse {
        return suspendCoroutine { continuation ->
            try {
                val requestJson = json.encodeToString(request)
                val requestBody = requestJson.toRequestBody(jsonMediaType)
                val apiUrl = "$baseUrl/chat/completions"

                val httpRequest = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer ${requireApiKey()}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(httpRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("API_ERROR", "请求失败: ${e.message}", e)
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string()
                            if (!response.isSuccessful) {
                                continuation.resumeWithException(
                                    IOException("API请求失败: ${response.code} $responseBody")
                                )
                                return
                            }
                            if (responseBody == null) {
                                continuation.resumeWithException(IOException("响应体为空"))
                                return
                            }
                            continuation.resume(json.decodeFromString<DeepseekResponse>(responseBody))
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        } finally {
                            response.close()
                        }
                    }
                })
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    fun makeStreamingApiRequest(request: DeepseekRequest, listener: StreamResponseListener) {
        try {
            val streamingRequest = request.copy(stream = true)
            val requestJson = json.encodeToString(streamingRequest)
            val requestBody = requestJson.toRequestBody(jsonMediaType)
            val apiUrl = "$baseUrl/chat/completions"

            val httpRequest = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer ${requireApiKey()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    listener.onError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        listener.onError(IOException("API流式请求失败: ${response.code}"))
                        response.close()
                        return
                    }
                    val responseBody = response.body
                    if (responseBody == null) {
                        listener.onError(IOException("响应体为空"))
                        response.close()
                        return
                    }
                    try {
                        val source = responseBody.source()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isEmpty()) continue
                            if (line == "[DONE]") {
                                listener.onComplete()
                                break
                            }
                            if (line.startsWith("data: ")) {
                                val jsonData = line.substring(6)
                                try {
                                    val chunkResponse = json.decodeFromString<DeepseekResponse>(jsonData)
                                    val content = chunkResponse.choices.firstOrNull()?.delta?.content
                                    if (content != null) listener.onContent(content)
                                } catch (e: Exception) {
                                    Log.e("API_STREAM_ERROR", "解析流式数据出错: ${e.message}", e)
                                }
                            }
                        }
                        listener.onComplete()
                    } catch (e: Exception) {
                        listener.onError(e)
                    } finally {
                        response.close()
                    }
                }
            })
        } catch (e: Exception) {
            listener.onError(e)
        }
    }
}
