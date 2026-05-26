package com.example.chat.di

import com.example.chat.model.ApiConfig
import com.example.chat.data.repository.SettingsManager
import okhttp3.Interceptor
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NetworkModuleTest {

    private val settingsManager: SettingsManager = mock()

    @Test
    fun testInterceptor_baseUrlWithoutV1() {
        val client = NetworkModule.provideOkHttpClient(settingsManager)
        val interceptor = client.interceptors[0]

        whenever(settingsManager.getConfigSync()).thenReturn(
            ApiConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "test-key",
                model = "deepseek-v3"
            )
        )

        val mockChain: Interceptor.Chain = mock()
        val originalRequest = Request.Builder()
            .url("https://placeholder.local/chat/completions")
            .build()
        whenever(mockChain.request()).thenReturn(originalRequest)

        val captor = argumentCaptor<Request>()
        whenever(mockChain.proceed(captor.capture())).thenReturn(mock())

        interceptor.intercept(mockChain)

        val interceptedRequest = captor.firstValue
        assertEquals("https://api.openai.com/v1/chat/completions", interceptedRequest.url.toString())
        assertEquals("Bearer test-key", interceptedRequest.header("Authorization"))
    }

    @Test
    fun testInterceptor_baseUrlWithV1() {
        val client = NetworkModule.provideOkHttpClient(settingsManager)
        val interceptor = client.interceptors[0]

        whenever(settingsManager.getConfigSync()).thenReturn(
            ApiConfig(
                baseUrl = "https://api.openai.com/v1",
                apiKey = "test-key",
                model = "deepseek-v3"
            )
        )

        val mockChain: Interceptor.Chain = mock()
        val originalRequest = Request.Builder()
            .url("https://placeholder.local/chat/completions")
            .build()
        whenever(mockChain.request()).thenReturn(originalRequest)

        val captor = argumentCaptor<Request>()
        whenever(mockChain.proceed(captor.capture())).thenReturn(mock())

        interceptor.intercept(mockChain)

        val interceptedRequest = captor.firstValue
        assertEquals("https://api.openai.com/v1/chat/completions", interceptedRequest.url.toString())
    }

    @Test
    fun testInterceptor_duplicateV1Prevention() {
        val client = NetworkModule.provideOkHttpClient(settingsManager)
        val interceptor = client.interceptors[0]

        whenever(settingsManager.getConfigSync()).thenReturn(
            ApiConfig(
                baseUrl = "https://api.openai.com/v1",
                apiKey = "test-key",
                model = "deepseek-v3"
            )
        )

        val mockChain: Interceptor.Chain = mock()
        val originalRequest = Request.Builder()
            .url("https://placeholder.local/v1/chat/completions")
            .build()
        whenever(mockChain.request()).thenReturn(originalRequest)

        val captor = argumentCaptor<Request>()
        whenever(mockChain.proceed(captor.capture())).thenReturn(mock())

        interceptor.intercept(mockChain)

        val interceptedRequest = captor.firstValue
        assertEquals("https://api.openai.com/v1/chat/completions", interceptedRequest.url.toString())
    }

    @Test
    fun testInterceptor_fullUrlEndpoint() {
        val client = NetworkModule.provideOkHttpClient(settingsManager)
        val interceptor = client.interceptors[0]

        whenever(settingsManager.getConfigSync()).thenReturn(
            ApiConfig(
                baseUrl = "https://custom-proxy.com/chat/completions",
                apiKey = "test-key",
                model = "deepseek-v3"
            )
        )

        val mockChain: Interceptor.Chain = mock()
        val originalRequest = Request.Builder()
            .url("https://placeholder.local/chat/completions")
            .build()
        whenever(mockChain.request()).thenReturn(originalRequest)

        val captor = argumentCaptor<Request>()
        whenever(mockChain.proceed(captor.capture())).thenReturn(mock())

        interceptor.intercept(mockChain)

        val interceptedRequest = captor.firstValue
        assertEquals("https://custom-proxy.com/chat/completions", interceptedRequest.url.toString())
    }
}
