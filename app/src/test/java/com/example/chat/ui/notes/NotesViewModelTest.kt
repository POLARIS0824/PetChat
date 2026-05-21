package com.example.chat.ui.notes

import com.example.chat.data.dao.NotesDao
import com.example.chat.data.entity.NoteEntity
import com.example.chat.data.repository.NotesRepository
import com.example.chat.model.NotesUiState
import com.example.chat.model.PetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeNotesDao : NotesDao {
        val notesState = MutableStateFlow<List<NoteEntity>>(emptyList())

        override fun getByTypeFlow(petType: String): Flow<List<NoteEntity>> {
            return notesState.map { list -> list.filter { it.petType == petType } }
        }

        override fun getAllFlow(): Flow<List<NoteEntity>> {
            return notesState
        }

        override suspend fun insert(note: NoteEntity) {
            val current = notesState.value.toMutableList()
            val newId = if (note.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1 else note.id
            current.add(note.copy(id = newId))
            current.sortByDescending { it.timestamp }
            notesState.value = current
        }

        override suspend fun delete(note: NoteEntity) {
            val current = notesState.value.toMutableList()
            current.removeAll { it.id == note.id }
            notesState.value = current
        }

        override suspend fun update(note: NoteEntity) {
            val current = notesState.value.toMutableList()
            val index = current.indexOfFirst { it.id == note.id }
            if (index != -1) {
                current[index] = note
                current.sortByDescending { it.timestamp }
                notesState.value = current
            }
        }
    }

    private lateinit var fakeDao: FakeNotesDao
    private lateinit var repository: NotesRepository
    private lateinit var viewModel: NotesViewModel

    @Before
    fun setUp() {
        fakeDao = FakeNotesDao()
        repository = NotesRepository(fakeDao)
        viewModel = NotesViewModel(repository)
    }

    @Test
    fun testInitialState_isLoading() = runTest {
        // 初始状态下由于 coroutine flow combine，在没有发出值之前是 Loading
        val initialState = viewModel.uiState.value
        assertTrue(initialState is NotesUiState.Loading)

        // 之后收集到 Ready 状态
        val state = viewModel.uiState.first { it is NotesUiState.Ready }
        assertTrue(state is NotesUiState.Ready)
        val readyState = state as NotesUiState.Ready
        assertTrue(readyState.notes.isEmpty())
        assertNull(readyState.selectedPetType)
    }

    @Test
    fun testAddAndRetrieveNotes() = runTest {
        // 添加一条猫咪便利贴
        viewModel.addNote("给猫咪买猫草", PetType.CAT.name)
        
        // 触发 Flow 收集
        val state = viewModel.uiState.first()
        assertTrue(state is NotesUiState.Ready)
        val readyState = state as NotesUiState.Ready
        assertEquals(1, readyState.notes.size)
        assertEquals("给猫咪买猫草", readyState.notes[0].content)
        assertEquals(PetType.CAT, readyState.notes[0].petType)
    }

    @Test
    fun testFilterNotesByPetType() = runTest {
        // 添加猫咪和狗狗各一条便利贴
        viewModel.addNote("给布丁梳毛", PetType.CAT.name)
        viewModel.addNote("带大白去公园", PetType.DOG.name)

        // 默认不过滤，应能查出所有
        var state = viewModel.uiState.first()
        var readyState = state as NotesUiState.Ready
        assertEquals(2, readyState.notes.size)

        // 设置过滤为 CAT
        viewModel.setFilter(PetType.CAT.name)
        
        state = viewModel.uiState.first()
        readyState = state as NotesUiState.Ready
        assertEquals(1, readyState.notes.size)
        assertEquals("给布丁梳毛", readyState.notes[0].content)
        assertEquals(PetType.CAT, readyState.notes[0].petType)

        // 设置过滤为 DOG
        viewModel.setFilter(PetType.DOG.name)
        
        state = viewModel.uiState.first()
        readyState = state as NotesUiState.Ready
        assertEquals(1, readyState.notes.size)
        assertEquals("带大白去公园", readyState.notes[0].content)
        assertEquals(PetType.DOG, readyState.notes[0].petType)
    }

    @Test
    fun testDeleteNote() = runTest {
        viewModel.addNote("临时便签", PetType.CAT.name)

        var state = viewModel.uiState.first()
        var readyState = state as NotesUiState.Ready
        assertEquals(1, readyState.notes.size)

        val noteToDelete = readyState.notes[0]
        viewModel.deleteNote(noteToDelete)

        state = viewModel.uiState.first()
        readyState = state as NotesUiState.Ready
        assertTrue(readyState.notes.isEmpty())
    }

    @Test
    fun testUpdateNote() = runTest {
        viewModel.addNote("旧内容", PetType.CAT.name)

        var state = viewModel.uiState.first()
        var readyState = state as NotesUiState.Ready
        assertEquals(1, readyState.notes.size)

        val noteToUpdate = readyState.notes[0].copy(content = "已更新内容")
        viewModel.updateNote(noteToUpdate)

        state = viewModel.uiState.first()
        readyState = state as NotesUiState.Ready
        assertEquals(1, readyState.notes.size)
        assertEquals("已更新内容", readyState.notes[0].content)
    }
}
