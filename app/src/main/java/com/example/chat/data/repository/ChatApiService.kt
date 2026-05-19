package com.example.chat.data.repository

import android.util.Log
import com.example.chat.BuildConfig
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.DeepseekResponse
import com.example.chat.model.StreamResponseListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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
        return suspendCancellableCoroutine { continuation ->
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

                val call = client.newCall(httpRequest)
                continuation.invokeOnCancellation { call.cancel() }

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("API_ERROR", "请求失败: ${e.message}", e)
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string()
                            if (!response.isSuccessful) {
                                if (continuation.isActive) continuation.resumeWithException(
                                    IOException("API请求失败: ${response.code} $responseBody")
                                )
                                return
                            }
                            if (responseBody == null) {
                                if (continuation.isActive) continuation.resumeWithException(IOException("响应体为空"))
                                return
                            }
                            if (continuation.isActive) continuation.resume(json.decodeFromString<DeepseekResponse>(responseBody))
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        } finally {
                            response.close()
                        }
                    }
                })
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    }

    fun makeStreamingApiRequest(request: DeepseekRequest): Flow<String> = callbackFlow {
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

        val call = client.newCall(httpRequest)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    close(IOException("API流式请求失败: ${response.code}"))
                    response.close()
                    return
                }
                val responseBody = response.body
                if (responseBody == null) {
                    close(IOException("响应体为空"))
                    response.close()
                    return
                }
                try {
                    val source = responseBody.source()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isEmpty()) continue
                        if (line.startsWith("data: ")) {
                            val jsonData = line.substring(6)
                            if (jsonData == "[DONE]") {
                                close()
                                return
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
                    response.close()
                }
            }
        })

        awaitClose { call.cancel() }
    }

    suspend fun makeStreamingApiRequestLegacy(request: DeepseekRequest, listener: StreamResponseListener) {
        try {
            makeStreamingApiRequest(request).collect { content ->
                listener.onContent(content)
            }
            listener.onComplete()
        } catch (e: Exception) {
            listener.onError(e)
        }
    }
}
