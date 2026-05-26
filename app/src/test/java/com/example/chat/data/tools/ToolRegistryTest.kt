package com.example.chat.data.tools

import com.example.chat.model.ApiTool
import com.example.chat.model.FunctionDefinition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ToolRegistryTest {

    private val mockTool1: Tool = mock()
    private val mockTool2: Tool = mock()
    private lateinit var registry: ToolRegistry

    @Before
    fun setUp() {
        whenever(mockTool1.name).thenReturn("tool_one")
        whenever(mockTool1.displayName).thenReturn("工具一")
        whenever(mockTool1.description).thenReturn("第一个工具")
        whenever(mockTool1.parametersJson).thenReturn("""{"type":"object","properties":{}}""")

        whenever(mockTool2.name).thenReturn("tool_two")
        whenever(mockTool2.displayName).thenReturn("工具二")
        whenever(mockTool2.description).thenReturn("第二个工具")
        whenever(mockTool2.parametersJson).thenReturn("""{"type":"object","properties":{}}""")

        registry = ToolRegistry(setOf(mockTool1, mockTool2))
    }

    @Test
    fun testGetApiTools_returnsAllToolDefinitions() {
        val tools = registry.getApiTools()
        assertEquals(2, tools.size)
        assertEquals("tool_one", tools[0].function.name)
        assertEquals("tool_two", tools[1].function.name)
    }

    @Test
    fun testExecuteTool_delegatesToCorrectTool() = runTest {
        whenever(mockTool1.execute("""{"arg":"value"}""")).thenReturn(
            ToolResult(true, "done", "完成")
        )

        val result = registry.executeTool("tool_one", """{"arg":"value"}""")
        assertTrue(result.success)
        assertEquals("done", result.content)
        verify(mockTool1).execute("""{"arg":"value"}""")
    }

    @Test
    fun testExecuteTool_unknownToolReturnsError() = runTest {
        val result = registry.executeTool("nonexistent", "{}")
        assertFalse(result.success)
        assertTrue(result.content.contains("未知工具"))
    }

    @Test
    fun testGetDisplayName_returnsCorrectName() {
        assertEquals("工具一", registry.getDisplayName("tool_one"))
        assertEquals("工具二", registry.getDisplayName("tool_two"))
        assertNull(registry.getDisplayName("nonexistent"))
    }
}
