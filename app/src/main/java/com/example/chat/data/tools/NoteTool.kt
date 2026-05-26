package com.example.chat.data.tools

import com.example.chat.data.entity.NoteEntity
import com.example.chat.data.repository.NotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteTool @Inject constructor(
    private val notesRepository: NotesRepository
) : Tool {
    override val name = "manage_notes"
    override val displayName = "管理笔记"
    override val description = "创建、查看或删除主人的笔记/便签。可执行的操作：create(创建笔记)、list(查看所有笔记)、delete(删除笔记)"

    override val parametersJson = """
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["create", "list", "delete"],
                    "description": "要执行的操作类型"
                },
                "content": {
                    "type": "string",
                    "description": "笔记内容，仅在action为create时需要"
                },
                "note_id": {
                    "type": "integer",
                    "description": "要删除的笔记ID，仅在action为delete时需要"
                }
            },
            "required": ["action"]
        }
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(arguments: String): ToolResult {
        return try {
            val args = json.decodeFromString<NoteActionArgs>(arguments)
            when (args.action) {
                "create" -> createNote(args.content ?: "")
                "list" -> listNotes()
                "delete" -> deleteNote(args.note_id ?: 0)
                else -> ToolResult(false, "未知操作: ${args.action}", "不支持的笔记操作")
            }
        } catch (e: Exception) {
            ToolResult(false, "笔记操作失败: ${e.message}", "笔记操作失败")
        }
    }

    private suspend fun createNote(content: String): ToolResult {
        if (content.isBlank()) return ToolResult(false, "笔记内容不能为空", "笔记内容为空")
        val note = NoteEntity(content = content, petType = "GENERAL")
        notesRepository.insertNote(note)
        return ToolResult(true, "笔记已创建: $content", "已创建笔记")
    }

    private suspend fun listNotes(): ToolResult {
        val notes = notesRepository.getAllNotesFlow().first()
        if (notes.isEmpty()) return ToolResult(true, "当前没有任何笔记", "没有笔记")
        val list = notes.joinToString("\n") { "[${it.id}] ${it.content}" }
        return ToolResult(true, "当前笔记:\n$list", "已查看${notes.size}条笔记")
    }

    private suspend fun deleteNote(noteId: Long): ToolResult {
        if (noteId <= 0) return ToolResult(false, "无效的笔记ID", "笔记ID无效")
        val notes = notesRepository.getAllNotesFlow().first()
        val target = notes.find { it.id == noteId }
            ?: return ToolResult(false, "未找到ID为${noteId}的笔记", "笔记不存在")
        notesRepository.deleteNote(target)
        return ToolResult(true, "笔记已删除: ${target.content}", "已删除笔记")
    }

    @Serializable
    private data class NoteActionArgs(
        val action: String,
        val content: String? = null,
        val note_id: Long? = null
    )
}
