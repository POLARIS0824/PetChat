# PetChat Agent Loop Implementation Plan

## Context

PetChat is currently a simple "send message → get AI response" chat app. To give it real value and make it interview-worthy, we need to transform the pets from passive responders into **active agents** that can use tools (function calling) to help the user with real tasks.

This plan implements a complete Agent Loop with 3 MVP tools (Notes CRUD, Timer Reminders, Memory Search), visible tool execution status in the chat UI, and native DeepSeek function calling protocol.

## Architecture Overview

```
User Input
  → PetChatViewModel.sendMessageAgent()
    → ChatRepository.getPetAgentResponse()
      → [Loop: max 5 iterations]
        1. Build messages with system prompt + context + tools[]
        2. ChatApiService.makeAgentStreamingRequest() → Flow<StreamEvent>
        3. Parse SSE stream: delta.content OR delta.tool_calls
        4. If tool_calls received:
           - ToolRegistry.executeTool(name, args)
           - Notify UI via AgentStreamListener.onToolCallStart/Complete
           - Add tool results to messages, loop back to step 1
        5. If no tool_calls: final text response → onComplete
      → UI: tool status bubbles + streaming text in chat
```

## Implementation Phases

### Phase 1: API Models (foundation for everything)

**File: `app/src/main/java/com/example/chat/model/ApiModels.kt`**

Add serializable types for function calling:

- `FunctionDefinition(name, description, parameters: Map<String, JsonElement>)` — JSON Schema for one tool
- `ApiTool(type="function", function: FunctionDefinition)` — wrapper for API request
- `ToolCall(id, type="function", function: FunctionCall?)` — one tool call in response
- `FunctionCall(name, arguments: String?)` — function name + JSON args string
- `ToolCallDelta(index, id?, type?, function: FunctionCallDelta?)` — incremental tool call in SSE
- `FunctionCallDelta(name?, arguments?)` — incremental function data in SSE

Extend existing models (all new fields nullable for backward compat):

- `Message` + `tool_calls: List<ToolCall>?`, `tool_call_id: String?`, `name: String?`
- `DeepseekRequest` + `tools: List<ApiTool>?`, `tool_choice: String?`
- `Delta` + `tool_calls: List<ToolCallDelta>?`

Add sealed class for structured streaming:

- `StreamEvent` sealed class: `Content(text)`, `ToolCallDeltaEvent(index, id?, functionName?, argumentsDelta?)`, `StreamFinished`

### Phase 2: SSE Parser Update

**File: `app/src/main/java/com/example/chat/data/repository/ChatApiService.kt`**

Add new method `makeAgentStreamingRequest(request: DeepseekRequest): Flow<StreamEvent>`:
- Reuse the same SSE line-reading loop
- After parsing each `data:` line as `DeepseekResponse`:
  - If `delta.content` present → emit `StreamEvent.Content`
  - If `delta.tool_calls` present → emit `StreamEvent.ToolCallDeltaEvent` for each entry
  - When `[DONE]` received → emit `StreamEvent.StreamFinished` then close
- Keep existing `makeStreamingApiRequest()` unchanged for backward compat

**New file: `app/src/main/java/com/example/chat/data/repository/ToolCallAccumulator.kt`**

Utility to accumulate incremental tool_call deltas across SSE chunks:
- Map of `index → MutableToolCall(id, functionName, arguments: StringBuilder)`
- `apply(event: ToolCallDeltaEvent)` — merges chunks
- `toToolCalls(): List<ToolCall>` — produces final list
- `hasPendingCalls(): Boolean`, `clear()`

### Phase 3: Tool Interface & Registry

**New file: `app/src/main/java/com/example/chat/data/tools/Tool.kt`**

```kotlin
interface Tool {
    val name: String
    val displayName: String
    val description: String
    val parametersJson: String  // JSON Schema for API

    suspend fun execute(arguments: String): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val content: String,           // fed back to LLM
    val displayMessage: String     // shown in UI status bubble
)
```

**New file: `app/src/main/java/com/example/chat/data/tools/ToolRegistry.kt`**

`@Singleton` class injected with `Set<Tool>` via Hilt multibinding:
- `getToolDefinitions(): List<Tool>`
- `getApiTools(): List<ApiTool>` — generates tools array for API request
- `executeTool(name, args): ToolResult` — dispatch to correct tool
- `getDisplayName(name): String?`

**New file: `app/src/main/java/com/example/chat/di/ToolModule.kt`**

Hilt module using `@Binds @IntoSet` for each concrete tool.

### Phase 4: Concrete Tool Implementations

**New file: `app/src/main/java/com/example/chat/data/tools/NoteTool.kt`**

Inject `NotesRepository`. Supports 3 actions via JSON args:
- `{"action":"create","content":"..."}` → `notesRepository.insertNote(NoteEntity(...))`
- `{"action":"list"}` → `notesRepository.getAllNotesFlow().first()` → formatted list
- `{"action":"delete","note_id":123}` → `notesRepository.deleteNote(...)`

**New file: `app/src/main/java/com/example/chat/data/tools/ReminderTool.kt`**

Inject `ReminderDao` + `@ApplicationContext Context`. Supports:
- `{"description":"喂猫","delay_minutes":5}` → schedule `ReminderWorker` via WorkManager
- `{"description":"开会","time":"2026-05-26T15:00:00"}` → parse absolute time
- Returns confirmation with scheduled time

**New file: `app/src/main/java/com/example/chat/data/tools/MemorySearchTool.kt`**

Inject `ChatDao` + `SessionManager`. Supports:
- `{"query":"最喜欢的食物","pet_type":"CAT"}`
- Queries `chatDao.getImportantMessages(sessionId)` + `chatDao.getSessionMessages()`
- Simple keyword relevance scoring (contains match)
- Returns formatted list of relevant memories

### Phase 5: Agent Loop in ChatRepository

**File: `app/src/main/java/com/example/chat/data/repository/ChatRepository.kt`**

New interface `AgentStreamListener`:
```kotlin
interface AgentStreamListener {
    fun onContent(content: String)
    fun onThinking()
    fun onToolCallStart(toolName: String, displayName: String)
    fun onToolCallComplete(toolName: String, displayName: String, result: ToolResult)
    fun onComplete()
    fun onError(e: Exception)
}
```

New method `getPetAgentResponse(petType, userMessage, listener)`:
1. Build messages: system prompt + context (up to 15 recent messages) + user input
2. Add `tools` array from `ToolRegistry.getApiTools()` to request
3. Loop (max 5 iterations):
   a. Call `apiService.makeAgentStreamingRequest(request)`
   b. Accumulate content tokens → forward to `listener.onContent()`
   c. Accumulate tool_calls via `ToolCallAccumulator`
   d. If tool_calls present after stream ends:
      - Add assistant message (with tool_calls) to messages list
      - Execute each tool, notify `listener.onToolCallStart/Complete`
      - Add `role: "tool"` result messages to messages list
      - `listener.onThinking()`, continue loop
   e. If no tool_calls: final text response, `listener.onComplete()`, return
4. If max iterations reached: inject system message asking for final response, make one last call without tools

Key design decisions:
- Context window: keep pairs of (assistant with tool_calls, tool result) together, never truncate mid-pair
- Tool execution on `Dispatchers.IO` via `withContext`
- If a tool fails, send error content back to LLM so it can recover

### Phase 6: Reminder Infrastructure

**New file: `app/src/main/java/com/example/chat/data/entity/ReminderEntity.kt`**
- `id`, `description`, `scheduledTimeMillis`, `petType`, `sessionId`, `isCompleted`, `createdAt`

**New file: `app/src/main/java/com/example/chat/data/dao/ReminderDao.kt`**
- `insert()`, `markCompleted(id)`, `getPendingReminders(now: Long)`

**New file: `app/src/main/java/com/example/chat/service/ReminderWorker.kt`**
- `@HiltWorker` with `CoroutineWorker`
- `doWork()`: query pending reminders, post notification, mark completed
- Scheduled as `OneTimeWorkRequest` with `setInitialDelay()` from `ReminderTool`

**Update `ChatDatabase.kt`**: Add `ReminderEntity` to entities, `reminderDao()` abstract method, bump version to 9

**Update `DatabaseModule.kt`**: Add `@Provides fun provideReminderDao(db): ReminderDao`

### Phase 7: UI Models Update

**File: `app/src/main/java/com/example/chat/model/ChatModels.kt`**

Extend `ChatMessage`:
```kotlin
data class ChatMessage(
    // ... existing fields ...
    val toolCallInfo: ToolCallInfo? = null,
)

data class ToolCallInfo(
    val toolName: String,
    val displayName: String,
    val status: ToolStatus,
    val resultPreview: String? = null,
)

enum class ToolStatus { EXECUTING, COMPLETED, FAILED }
```

Messages with `toolCallInfo != null` are rendered as tool status bubbles, NOT saved to DB.

**File: `app/src/main/java/com/example/chat/model/ChatUiState.kt`**

Add to `Ready`:
```kotlin
val agentStatus: AgentStatus? = null,  // THINKING or EXECUTING
```

### Phase 8: ViewModel Update

**File: `app/src/main/java/com/example/chat/ui/chat/PetChatViewModel.kt`**

Replace `sendMessageStreaming` with new agent path (make agent the default):

New method `sendMessage(message)` flow:
1. Same setup: set streaming flags, append user message, save to DB
2. Create empty assistant message (same as before)
3. Create `AgentStreamListener`:
   - `onContent(text)` → append to buffer, update streaming message (same logic)
   - `onToolCallStart(name, display)` → insert `ChatMessage(role="tool_status", toolCallInfo=...)` into chatHistory, set agentStatus=EXECUTING
   - `onToolCallComplete(name, display, result)` → update tool status bubble to COMPLETED/FAILED, clear agentStatus
   - `onThinking()` → set agentStatus=THINKING
   - `onComplete()` → finalize message, save to DB, trigger analysis (same as before)
   - `onError(e)` → show error fallback (same as before)
4. Call `repository.getPetAgentResponse(petType, message, listener)`

Important: `role == "tool_status"` messages are NEVER saved to DB (skip in `onComplete` save).

### Phase 9: UI Components Update

**File: `app/src/main/java/com/example/chat/ui/chat/ChatComponents.kt`**

Add `ToolStatusBubble` composable:
- Centered, muted background, compact design
- Shows icon + text based on status: "🔧 正在记笔记...", "✅ 笔记已创建", "❌ 操作失败"
- Lightweight, non-interactive

Update `ChatBubble`:
- At the top of the composable, if `message.toolCallInfo != null`, render `ToolStatusBubble` and return early
- No other changes needed

**File: `app/src/main/java/com/example/chat/ui/chat/ChatScreen.kt`**

No structural changes needed. The LazyColumn already renders each message via `ChatBubble`, and tool status messages flow through the same pipeline.

### Phase 10: Tests

**New test files:**
- `data/tools/NoteToolTest.kt` — test create/list/delete with mocked NotesRepository
- `data/tools/ReminderToolTest.kt` — test schedule with mocked dependencies
- `data/tools/MemorySearchToolTest.kt` — test search with mocked ChatDao
- `data/tools/ToolRegistryTest.kt` — test registration, dispatch, unknown tool

**Update existing tests:**
- `ChatApiServiceTest.kt` — add tests for SSE parsing with tool_calls delta chunks
- `ChatRepositoryTest.kt` — add tests for agent loop: single tool call, multi-turn, max iterations, tool failure recovery
- `PetChatViewModelTest.kt` — add tests: tool status messages in chatHistory, tool status not saved to DB, agent streaming content, error handling

## Verification

1. **Build**: `./gradlew assembleDebug` must succeed
2. **Unit tests**: `./gradlew test` — all existing and new tests pass
3. **Integration test (manual)**: Deploy to device (`./gradlew installDebug`) and test:
   - Send "帮我记一下，明天下午3点带布丁去打疫苗" → verify note is created, tool status bubble appears
   - Send "我之前说过喜欢什么？" → verify memory search finds relevant info
   - Send "5分钟后提醒我喂猫" → verify reminder is scheduled, notification fires
   - Send normal chat message → verify no tool calls, works like before
4. **Edge cases**: Tool failure (disconnect network mid-tool), max iterations (keep asking for tools), empty arguments
