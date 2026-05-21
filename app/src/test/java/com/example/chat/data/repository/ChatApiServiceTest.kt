package com.example.chat.data.repository

import android.util.Log
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.DeepseekResponse
import com.example.chat.model.Message
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChatApiServiceTest {

    private val deepseekApi: DeepseekApi = mock()
    private lateinit var apiService: ChatApiService
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setUp() {
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.e(any(), any(), any()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(any(), any()) }.thenReturn(0)

        apiService = ChatApiService(deepseekApi)
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun testMakeStreamingApiRequest_emitsDeltaContentAndClosesSuccessfully() = runTest {
        val request = DeepseekRequest(
            model = "deepseek-v3",
            messages = listOf(Message("user", "Hello")),
            stream = true
        )

        val sseContent = """
            data: {"choices":[{"delta":{"content":"Hi"}}]}
            
            data: {"choices":[{"delta":{"content":" there"}}]}
            
            data: [DONE]
            
        """.trimIndent()

        val responseBody = sseContent.toResponseBody("text/event-stream".toMediaTypeOrNull())
        whenever(deepseekApi.chatCompletionsStreaming(any())).thenReturn(responseBody)

        val flow = apiService.makeStreamingApiRequest(request)
        val result = flow.toList()

        assertEquals(listOf("Hi", " there"), result)
    }
}
