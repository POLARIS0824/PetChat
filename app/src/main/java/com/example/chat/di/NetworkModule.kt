package com.example.chat.di

import com.example.chat.data.repository.DeepseekApi
import com.example.chat.data.repository.SettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(settingsManager: SettingsManager): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val config = settingsManager.getConfig()
                val original = chain.request()
                val configuredUrl = config.baseUrl.trim()
                
                val newUrl = if (configuredUrl.endsWith("/chat/completions") ||
                    configuredUrl.contains("/chat/completions") ||
                    configuredUrl.contains("/completions") ||
                    configuredUrl.contains("/generate")) {
                    configuredUrl.toHttpUrl()
                } else {
                    val configBase = configuredUrl.trimEnd('/')
                    val apiPath = original.url.encodedPath
                    
                    val finalUrlString = if (configBase.endsWith("/v1") && apiPath.startsWith("/v1/")) {
                        // 避免重复的 /v1
                        "${configBase.substring(0, configBase.length - 3)}${apiPath}"
                    } else if (!configBase.endsWith("/v1") && !configBase.contains("/v1/") &&
                               !apiPath.startsWith("/v1/") && !apiPath.startsWith("v1/")) {
                        // 若 Base URL 与端点路径都不带 /v1，且并非特殊的自定义路径，则自动插入 /v1
                        "${configBase}/v1${apiPath}"
                    } else {
                        "${configBase}${apiPath}"
                    }
                    finalUrlString.toHttpUrl()
                }
                val newRequest = original.newBuilder()
                    .url(newUrl)
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .build()

                try {
                    val requestBodyString = try {
                        val buffer = okio.Buffer()
                        newRequest.body?.writeTo(buffer)
                        buffer.readUtf8()
                    } catch (e: Exception) {
                        "Error reading body"
                    }
                    android.util.Log.d("API_REQUEST", "URL: $newUrl | Model: ${config.model} | Body: $requestBodyString")
                } catch (t: Throwable) {
                    // 保护块：防止 JVM 单元测试中 android.util.Log 未 Mock 报错
                }

                val response = chain.proceed(newRequest)

                try {
                    if (!response.isSuccessful) {
                        val errorBody = try {
                            response.peekBody(1024 * 1024).string()
                        } catch (e: Exception) {
                            "Error reading error body"
                        }
                        android.util.Log.e("API_RESPONSE", "Error Code: ${response.code} | Message: ${response.message} | Body: $errorBody")
                    } else {
                        android.util.Log.d("API_RESPONSE", "Success Code: ${response.code}")
                    }
                } catch (t: Throwable) {
                    // 保护块：防止 JVM 单元测试中 Mock 响应或 Log 报错
                }

                response
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val json = Json { 
            ignoreUnknownKeys = true 
            explicitNulls = false
        }
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://placeholder.local/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideDeepseekApi(retrofit: Retrofit): DeepseekApi {
        return retrofit.create(DeepseekApi::class.java)
    }
}
