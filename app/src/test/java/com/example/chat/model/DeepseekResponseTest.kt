package com.example.chat.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepseekResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDeserialize_choicesIsNull() {
        val input = """
            {"id":"c9f3283ecb6f89cbdc8375eef5b61bc2","object":"","created":0,"model":"deepseek/deepseek-v4-flash","choices":null,"system_fingerprint":"","sla_metrics":{"ttft_ms":738,"ts_us":1779365161623709}}
        """.trimIndent()

        val response = json.decodeFromString<DeepseekResponse>(input)
        assertNull(response.choices)
        assertEquals("deepseek/deepseek-v4-flash", response.model)
    }

    @Test
    fun testDeserialize_choicesIsEmpty() {
        val input = """
            {"id":"c9f3283ecb6f89cbdc8375eef5b61bc2","object":"","created":0,"model":"deepseek/deepseek-v4-flash","choices":[],"system_fingerprint":""}
        """.trimIndent()

        val response = json.decodeFromString<DeepseekResponse>(input)
        assertEquals(0, response.choices?.size)
    }

    @Test
    fun testDeserialize_choicesWithContent() {
        val input = """
            {"id":"c9f3283ecb6f89cbdc8375eef5b61bc2","choices":[{"delta":{"content":"hello"}}]}
        """.trimIndent()

        val response = json.decodeFromString<DeepseekResponse>(input)
        assertEquals(1, response.choices?.size)
        assertEquals("hello", response.choices?.get(0)?.delta?.content)
    }
}
