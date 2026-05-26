package com.example.chat.data.tools

import com.example.chat.data.entity.NoteEntity
import com.example.chat.data.repository.NotesRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class NoteToolTest {

    private val notesRepository: NotesRepository = mock()
    private val noteTool = NoteTool(notesRepository)

    @Test
    fun testCreateNote_success() = runTest {
        val result = noteTool.execute("""{"action":"create","content":"明天带布丁打疫苗"}""")

        assertTrue(result.success)
        assertTrue(result.content.contains("明天带布丁打疫苗"))
        assertEquals("已创建笔记", result.displayMessage)

        argumentCaptor<NoteEntity>().apply {
            verify(notesRepository).insertNote(capture())
            assertEquals("明天带布丁打疫苗", firstValue.content)
        }
    }

    @Test
    fun testCreateNote_blankContent() = runTest {
        val result = noteTool.execute("""{"action":"create","content":""}""")
        assertFalse(result.success)
        verify(notesRepository, never()).insertNote(any())
    }

    @Test
    fun testListNotes_empty() = runTest {
        whenever(notesRepository.getAllNotesFlow()).thenReturn(flowOf(emptyList()))
        val result = noteTool.execute("""{"action":"list"}""")
        assertTrue(result.success)
        assertTrue(result.content.contains("没有任何笔记"))
    }

    @Test
    fun testListNotes_withItems() = runTest {
        val notes = listOf(
            NoteEntity(id = 1L, content = "笔记1", petType = "GENERAL"),
            NoteEntity(id = 2L, content = "笔记2", petType = "GENERAL")
        )
        whenever(notesRepository.getAllNotesFlow()).thenReturn(flowOf(notes))
        val result = noteTool.execute("""{"action":"list"}""")
        assertTrue(result.success)
        assertTrue(result.content.contains("笔记1"))
        assertTrue(result.content.contains("笔记2"))
    }

    @Test
    fun testDeleteNote_notFound() = runTest {
        whenever(notesRepository.getAllNotesFlow()).thenReturn(flowOf(emptyList()))
        val result = noteTool.execute("""{"action":"delete","note_id":999}""")
        assertFalse(result.success)
    }

    @Test
    fun testDeleteNote_success() = runTest {
        val note = NoteEntity(id = 1L, content = "要删除的笔记", petType = "GENERAL")
        whenever(notesRepository.getAllNotesFlow()).thenReturn(flowOf(listOf(note)))
        val result = noteTool.execute("""{"action":"delete","note_id":1}""")
        assertTrue(result.success)
        verify(notesRepository).deleteNote(note)
    }
}
