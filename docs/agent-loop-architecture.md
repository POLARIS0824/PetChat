# PetChat Agent Loop 架构文档

## 什么是 Agent Loop

传统聊天应用的流程是单向的：**用户发消息 → AI 回复文本 → 结束**。

Agent Loop 把这个流程升级为闭环：

```
用户发消息
  → AI 思考（要调用工具吗？）
    → 如果需要：AI 返回 tool_call → App 执行工具 → 结果回传 AI → 继续思考
    → 如果不需要：AI 返回纯文本 → 结束
```

这叫 **ReAct (Reasoning and Acting) 模式**——AI 不再只会"说话"，而是能"思考和行动"。

---

## 整体架构

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (Compose)                                     │
│  ChatScreen → ChatBubble / ToolStatusBubble             │
└──────────────────────┬──────────────────────────────────┘
                       │ StateFlow<ChatUiState>
┌──────────────────────▼──────────────────────────────────┐
│  PetChatViewModel                                       │
│  sendMessageAgent() → AgentStreamListener 回调           │
│  onContent / onToolCallStart / onToolCallComplete        │
└──────────────────────┬──────────────────────────────────┘
                       │ 调用 getPetAgentResponse()
┌──────────────────────▼──────────────────────────────────┐
│  ChatRepository (Agent Loop 核心)                        │
│                                                         │
│  while (iteration < 5):                                 │
│    1. 构建 messages（system prompt + 历史 + user input） │
│    2. 附加 tools[] 定义到 API request                    │
│    3. 流式调用 LLM，解析 content + tool_calls            │
│    4. 如果有 tool_calls → 执行工具 → 结果回传 → 继续循环  │
│    5. 如果无 tool_calls → 最终文本 → onComplete          │
└──────┬──────────────────────────────┬───────────────────┘
       │                              │
┌──────▼──────────┐    ┌──────────────▼───────────────────┐
│ ChatApiService  │    │ ToolRegistry + Tool 实现          │
│ SSE 流解析      │    │ NoteTool / ReminderTool /        │
│ StreamEvent     │    │ MemorySearchTool                 │
│ Content/        │    │                                  │
│ ToolCallDelta   │    │ Hilt @Binds @IntoSet 注入         │
└─────────────────┘    └──────────────────────────────────┘
```

---

## 关键组件详解

### 1. API 模型层 (`model/ApiModels.kt`)

这是整个 Agent Loop 的**数据基础**。需要在原有纯文本聊天模型上扩展 function calling 相关的类型。

```kotlin
// === 请求端：告诉 LLM 有哪些工具可用 ===

@Serializable
data class DeepseekRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean,
    val tools: List<ApiTool>? = null,       // 可用工具列表
    val tool_choice: String? = null,         // "auto" | "none" | specific
)

@Serializable
data class ApiTool(
    val type: String = "function",
    val function: FunctionDefinition,        // name + description + JSON Schema
)

// === Message：扩展了 tool_call 相关字段 ===

@Serializable
data class Message(
    val role: String,                        // "system" | "user" | "assistant" | "tool"
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null,  // assistant 消息中携带的工具调用
    val tool_call_id: String? = null,        // tool 消息中回传的调用 ID
    val name: String? = null,                // tool 消息中的工具名
)

// === 响应端：解析 LLM 返回的 tool_calls ===

@Serializable
data class ToolCall(
    val id: String?,                         // 每次工具调用的唯一 ID
    val function: FunctionCall?,             // name + JSON arguments
)

// === 流式增量：SSE 流中 tool_calls 是逐 chunk 到达的 ===

@Serializable
data class ToolCallDelta(
    val index: Int?,                         // 第几个 tool call（从 0 开始）
    val id: String?,                         // 仅第一个 chunk 包含
    val function: FunctionCallDelta?,        // name + arguments 增量
)

@Serializable
data class FunctionCallDelta(
    val name: String?,                       // 函数名，第二个 chunk 到达
    val arguments: String?,                  // JSON 参数，逐 chunk 拼接
)
```

**关键点**：所有新增字段都用 `? = null` 默认值，保证向后兼容 —— 不带 tools 的请求就是原来的纯文本聊天。

### 2. 流式解析层 (`StreamEvent` 密封类)

SSE 流中交替出现两种数据：
- `delta.content` → 纯文本 token（和原来一样）
- `delta.tool_calls` → 工具调用增量（新的）

把二者抽象为 `StreamEvent` 密封类：

```kotlin
sealed class StreamEvent {
    data class Content(val text: String) : StreamEvent()
    data class ToolCallDeltaEvent(
        val index: Int,           // 第几个 tool call
        val id: String?,          // 第一次出现时携带
        val functionName: String?,// 第二次出现时携带
        val argumentsDelta: String? // 后续逐 chunk 携带
    ) : StreamEvent()
    data object StreamFinished : StreamEvent()
}
```

**为什么不用简单的 `Flow<String>` ？**

因为 tool_calls 不是文本，无法用 String 表达。需要结构化的类型来区分"这是给用户看的文字"还是"这是要执行的函数调用"。

### 3. ToolCallAccumulator（增量累加器）

SSE 流中 tool_calls 是分多个 chunk 到达的：

```
Chunk 1: {"delta":{"tool_calls":[{"index":0,"id":"call_abc","type":"function"}]}}
Chunk 2: {"delta":{"tool_calls":[{"index":0,"function":{"name":"set_reminder"}}]}}
Chunk 3: {"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"des"}}]}}
Chunk 4: {"delta":{"tool_calls":[{"index":0,"function":{"arguments":"cription\":\"喂猫\"}}"}}]}}
Chunk 5: {"finish_reason":"tool_calls"}
```

`ToolCallAccumulator` 负责把这些 chunk 拼接成完整的 `ToolCall`：

```kotlin
class ToolCallAccumulator {
    private val toolCalls = mutableMapOf<Int, MutableToolCall>()

    fun apply(event: StreamEvent.ToolCallDeltaEvent) {
        val entry = toolCalls.getOrPut(event.index) { MutableToolCall() }
        if (event.id != null) entry.id = event.id
        if (event.functionName != null) entry.functionName = event.functionName
        if (event.argumentsDelta != null) entry.arguments.append(event.argumentsDelta)
    }

    fun toToolCalls(): List<ToolCall> { /* 输出完整列表 */ }
    fun hasPendingCalls(): Boolean = toolCalls.isNotEmpty()
}
```

### 4. Tool 接口与注册中心 (`data/tools/`)

```kotlin
interface Tool {
    val name: String            // API 协议中的函数名，如 "set_reminder"
    val displayName: String     // UI 显示名，如 "设置提醒"
    val description: String     // 描述，LLM 据此决定何时调用
    val parametersJson: String  // JSON Schema，定义参数格式

    suspend fun execute(arguments: String): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val content: String,        // 回传给 LLM，让它知道执行结果
    val displayMessage: String  // 显示在 UI 状态气泡中
)
```

**设计要点**：
- `content` 是给 LLM 看的——可以包含详细的错误信息，LLM 会据此自我修正
- `displayMessage` 是给用户看的——简洁明了的操作结果
- 错误时 `success = false`，但 `content` 要详细告诉 LLM 错在哪、怎么修正

**Hilt 多绑定注入**：

```kotlin
// ToolModule.kt
@Binds @IntoSet
abstract fun bindNoteTool(noteTool: NoteTool): Tool

// ToolRegistry.kt
@Singleton
class ToolRegistry @Inject constructor(
    private val tools: Set<@JvmSuppressWildcards Tool>  // 自动收集所有 @IntoSet 的实现
)
```

新增工具只需：实现 `Tool` 接口 + 在 `ToolModule` 中加一行 `@Binds @IntoSet`。完全符合开闭原则。

### 5. Agent Loop 核心 (`ChatRepository.getPetAgentResponse()`)

```kotlin
suspend fun getPetAgentResponse(
    petType: PetType,
    userMessage: String,
    listener: AgentStreamListener  // 通知 ViewModel 更新 UI
) {
    val messages = buildAgentMessages(petType, userMessage).toMutableList()
    val tools = toolRegistry.getApiTools()

    var iteration = 0
    val MAX_AGENT_ITERATIONS = 5  // 防止无限循环

    while (iteration < MAX_AGENT_ITERATIONS) {
        // 1. 发送请求（带 tools）
        val request = DeepseekRequest(
            model = model, messages = messages,
            stream = true, tools = tools, tool_choice = "auto"
        )

        // 2. 流式解析响应
        val accumulator = ToolCallAccumulator()
        val contentBuffer = StringBuilder()

        apiService.makeAgentStreamingRequest(request).collect { event ->
            when (event) {
                is StreamEvent.Content -> {
                    contentBuffer.append(event.text)
                    listener.onContent(event.text)      // 实时推送到 UI
                }
                is StreamEvent.ToolCallDeltaEvent -> {
                    accumulator.apply(event)             // 累加 tool_call 片段
                }
                is StreamEvent.StreamFinished -> {}
            }
        }

        // 3. 判断：LLM 想调用工具吗？
        if (accumulator.hasPendingCalls()) {
            val toolCalls = accumulator.toToolCalls()

            // 3a. 把 assistant 消息（含 tool_calls）加入上下文
            messages.add(Message(
                role = "assistant",
                content = contentBuffer.toString().takeIf { it.isNotBlank() },
                tool_calls = toolCalls
            ))

            // 3b. 依次执行每个工具
            for (tc in toolCalls) {
                listener.onToolCallStart(tc.id, tc.function.name, displayName)
                val result = toolRegistry.executeTool(name, args)
                listener.onToolCallComplete(tc.id, tc.function.name, displayName, result)

                // 3c. 把工具结果加入上下文
                messages.add(Message(
                    role = "tool",
                    tool_call_id = tc.id,
                    name = tc.function.name,
                    content = result.content  // 成功或失败的详细信息
                ))
            }

            listener.onThinking()
            iteration++
            // 继续循环——LLM 会看到工具结果，决定下一步
        } else {
            // 4. 没有工具调用，最终回复
            listener.onComplete()
            return
        }
    }

    // 达到最大迭代次数，强制要求 LLM 给出最终回复（不带 tools）
    messages.add(Message("system", "已达到工具调用上限，请直接回复用户。"))
    // 最后一次调用不带 tools，确保返回纯文本
    ...
}
```

**循环终止条件**：
1. LLM 返回纯文本（无 tool_calls）→ `onComplete()`
2. 达到 5 轮迭代上限 → 强制要求最终回复
3. API 调用抛出异常 → `onError()`

### 6. AgentStreamListener 接口

```kotlin
interface AgentStreamListener {
    fun onContent(content: String)                                    // 流式文本 token
    fun onThinking()                                                   // LLM 正在思考（轮间）
    fun onToolCallStart(toolCallId: String, toolName: String, displayName: String)
    fun onToolCallComplete(toolCallId: String, toolName: String, displayName: String, result: ToolResult)
    fun onComplete()                                                   // 整个过程结束
    fun onError(e: Exception)
}
```

**为什么 `toolCallId` 很重要？**

DeepSeek 等模型支持**并行工具调用**——一次返回多个 `tool_call`（如用户说"帮我记一下明天打疫苗，顺便3点提醒我"）。如果没有 `toolCallId`，ViewModel 无法区分"记笔记"和"设提醒"哪个完成了，会把后完成的状态错误地写到先启动的气泡上。

### 7. ViewModel 层 (`PetChatViewModel`)

ViewModel 的核心职责是把 `AgentStreamListener` 的回调翻译成 UI 状态更新：

```
onContent(text)
  → 按 petMessage.id 精确替换 chatHistory 中的流式消息

onToolCallStart(toolCallId, name, display)
  → 插入 ChatMessage(role="tool_status", id=toolCallId, ...EXECUTING)
  → 设置 agentStatus = EXECUTING

onToolCallComplete(toolCallId, name, display, result)
  → 按 toolCallId 找到对应的 EXECUTING 气泡，替换为 COMPLETED/FAILED
  → 清除 agentStatus

onThinking()
  → 设置 agentStatus = THINKING

onComplete()
  → 清除所有 streaming 状态
  → 仅保存 finalMessage 到数据库（用户消息在发送时已保存）

onError(e)
  → 按 petMessage.id 替换为错误提示
```

### 8. UI 层 (`ChatComponents.kt`)

```kotlin
@Composable
fun ChatBubble(message: ChatMessage, isStreaming: Boolean) {
    // 工具状态消息走专用渲染
    if (message.toolCallInfo != null) {
        ToolStatusBubble(message.toolCallInfo)
        return
    }
    // 普通消息的原有渲染逻辑...
}

@Composable
fun ToolStatusBubble(toolCallInfo: ToolCallInfo) {
    // 居中显示，紧凑样式
    // EXECUTING → "🔧 管理笔记中..."
    // COMPLETED → "✅ 管理笔记 已完成"
    // FAILED    → "❌ 管理笔记 失败"
}
```

**关键设计**：工具状态消息 `role = "tool_status"`，**不保存到数据库**，仅存在于 UI 内存状态中。这是业界标准做法（ChatGPT、Gemini 都是这样）。

---

## 具体工具实现

### NoteTool — 管理笔记

支持 `create` / `list` / `delete` 三种操作。通过 JSON args 的 `action` 字段区分：

```json
{"action": "create", "content": "明天下午3点带布丁打疫苗"}
{"action": "list"}
{"action": "delete", "note_id": 42}
```

**自愈设计**：如果 `action` 值非法，返回详细的错误信息给 LLM，LLM 会自我修正重试。

### ReminderTool — 设置提醒

支持相对时间和绝对时间两种方式：

```json
{"description": "喂猫", "delay_minutes": 5}
{"description": "开会", "time": "2026-05-26T15:00:00"}
```

时间解析采用三级 fallback（见下方"易错点 5"）。

### MemorySearchTool — 搜索记忆

使用 **SQLite LIKE 查询**直接在数据库层过滤，避免加载全部消息到 JVM 内存：

```sql
SELECT * FROM chat_history
WHERE sessionId = :sessionId
  AND (isImportant = 1 OR content LIKE '%' || :query || '%')
ORDER BY timestamp DESC
LIMIT 15
```

### ReminderWorker — 提醒通知

@HiltWorker，通过 WorkManager 的 OneTimeWorkRequest 调度。到达时间后发出系统通知并标记为已完成。

---

## 易错点与实战踩坑记录

### 易错点 1：LazyColumn 重复 key 崩溃

**现象**：
```
java.lang.IllegalArgumentException: Key "ef251a6f-..." was already used.
```

**根因**：在 `onContent` 回调中用 `chatHistory.dropLast(1) + updatedMessage` 替换流式消息。当 Agent Loop 进入第二轮迭代时，chatHistory 的最后一条不是流式消息（而是第一轮插入的工具状态气泡），导致工具状态气泡被错误删除，而流式消息的旧副本和新副本同时存在。

**错误代码**：
```kotlin
override fun onContent(content: String) {
    val updatedMessage = petMessage.copy(content = responseBuffer.toString())
    updateReady { state ->
        state.copy(
            // ❌ dropLast(1) 假设最后一条一定是流式消息
            chatHistory = state.chatHistory.dropLast(1) + updatedMessage
        )
    }
}
```

**正确代码**：
```kotlin
override fun onContent(content: String) {
    val updatedMessage = petMessage.copy(content = responseBuffer.toString())
    updateReady { state ->
        state.copy(
            // ✅ 按 petMessage.id 精确查找替换，不依赖位置
            chatHistory = state.chatHistory.map {
                if (it.id == petMessage.id) updatedMessage else it
            }
        )
    }
}
```

**教训**：**永远不要用 `dropLast(1)` 来"替换最后一个元素"**。在多轮代理场景中，消息顺序不是你能预测的。用 ID 做精确查找和替换。

### 易错点 2：onComplete 中重存历史消息导致数据库膨胀 + 宠物串乱

**现象**：对话出现重复（同一条消息显示多次），切换宠物后看到其它宠物的历史记录。

**根因**：在 `onComplete` 中遍历整个 `chatHistory`，找出所有 `role == "user"` 的消息重新存入数据库。但 `chatHistory` 中包含从 DB 加载的旧消息，这些消息被用**当前宠物的 petType** 重新写入，导致：
1. 数据库中出现重复记录
2. 旧消息的 petType 被当前宠物覆盖（CAT 的消息变成了 DOG）

**错误代码**：
```kotlin
override fun onComplete() {
    viewModelScope.launch {
        // ❌ 遍历整个 chatHistory，把从 DB 加载的旧消息也重存了
        val messagesToSave = readyState().chatHistory
            .filter { it.role != "tool_status" && it.content.isNotBlank() }
            .filter { msg -> msg.id == finalMessage.id || msg.role == "user" }
        for (msg in messagesToSave) {
            repository.saveChatMessage(msg, petType)  // 用当前 petType 重存！
        }
    }
}
```

**正确代码**：
```kotlin
override fun onComplete() {
    viewModelScope.launch {
        // ✅ 只保存当前 AI 的最终回复
        // 用户消息在 sendMessageAgent() 开始时已经保存过了
        repository.saveChatMessage(finalMessage, petType)
    }
}
```

**教训**：**只保存当前对话轮次产生的新消息**。用户消息在发送时就保存，AI 消息在流式完成后保存。不要试图"同步整个 chatHistory"。

### 易错点 3：并行工具调用时的 UI 状态更新

**现象**：多个工具同时触发时，后完成的工具把先完成的状态气泡覆盖了。

**根因**：`AgentStreamListener.onToolCallStart` 没有 `toolCallId` 参数。ViewModel 在 `onComplete` 时无法区分"哪个工具完成了"，导致状态更新错位。

**错误设计**：
```kotlin
// ❌ 没有 toolCallId，无法区分多个并行工具调用
interface AgentStreamListener {
    fun onToolCallStart(toolName: String, displayName: String)
    fun onToolCallComplete(toolName: String, displayName: String, result: ToolResult)
}
```

**正确设计**：
```kotlin
// ✅ toolCallId 是 LLM 生成的唯一标识，用于精准匹配
interface AgentStreamListener {
    fun onToolCallStart(toolCallId: String, toolName: String, displayName: String)
    fun onToolCallComplete(toolCallId: String, toolName: String, displayName: String, result: ToolResult)
}
```

对应的 ViewModel 更新逻辑：
```kotlin
// onToolCallStart: 插入一个 ID = toolCallId 的待执行气泡
updateReady { it.copy(chatHistory = it.chatHistory + statusMsg.copy(id = toolCallId)) }

// onToolCallComplete: 按 toolCallId 找到对应气泡，替换为完成状态
updateReady {
    it.copy(chatHistory = it.chatHistory.map { msg ->
        if (msg.id == toolCallId && msg.role == "tool_status"
            && msg.toolCallInfo?.status == ToolStatus.EXECUTING)
            msg.copy(toolCallInfo = msg.toolCallInfo.copy(status = ToolStatus.COMPLETED))
        else msg
    })
}
```

### 易错点 4：SSE 流中 tool_calls 的增量拼接

**现象**：ToolCall 的 `arguments` 字段只有最后一个字符，或者解析失败。

**根因**：SSE 流中 `delta.tool_calls` 是**增量**的——每个 chunk 只包含一小段 JSON。比如 `{"description": "喂猫"}` 可能分 3 个 chunk 到达：
```
Chunk 1: {"d
Chunk 2: escription":
Chunk 3:  "喂猫"}
```

需要用 `ToolCallAccumulator` 把同一 `index` 的所有 chunk 的 `arguments` **拼接**起来，才能得到完整的 JSON。

**错误做法**：每次 chunk 到达时直接覆盖 `arguments` 字段。

**正确做法**：用 `StringBuilder` 累加，流结束后再解析完整 JSON。

### 易错点 5：LLM 生成的 ISO 时间格式不标准

**现象**：`java.time.Instant.parse("2026-05-27T08:00:00")` 抛出异常。

**根因**：`Instant.parse()` 要求 UTC 时区后缀 `Z`（如 `2026-05-27T08:00:00Z`），但 LLM 通常生成不带时区的本地时间字符串。这不是 LLM 的 bug——它确实不知道用户的时区。

**错误做法**：
```kotlin
java.time.Instant.parse(timeStr).toEpochMilli()  // ❌ 缺少 Z 后缀就抛异常
```

**正确做法**——三级 fallback：
```kotlin
private fun parseTime(timeStr: String): Long {
    return try {
        Instant.parse(timeStr).toEpochMilli()             // 1. 尝试直接解析
    } catch (e: Exception) {
        try {
            Instant.parse("${timeStr}Z").toEpochMilli()   // 2. 追加 Z 再试
        } catch (e2: Exception) {
            val localDt = LocalDateTime.parse(timeStr)     // 3. 按系统时区解析
            localDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}
```

### 易错点 6：Context 窗口增长导致成本爆炸

**现象**：Agent Loop 每轮迭代都在 `messages` 列表尾部追加内容，5 轮下来 context 可能达到几十条消息。

**根因**：每轮迭代至少追加：
- 1 条 assistant 消息（含 tool_calls）
- N 条 tool 消息（每个工具调用一条结果）

如果 LLM 每轮调用 3 个工具，5 轮下来就多了 20 条消息。加上初始的 system prompt 和聊天历史，很容易超出模型的 context 限制。

**缓解措施**：
1. **MAX_AGENT_ITERATIONS = 5**：硬上限，防止无限循环
2. **到达上限后强制终结**：注入 system 消息要求直接回复，且最后一次请求不带 tools
3. **buildAgentMessages 限制历史消息数**：只取最近 15 条（`AGENT_CONTEXT_LIMIT`）
4. **不截断 tool_call/result 对**：如果必须截断历史，至少要保证 tool_call 和对应的 tool result 成对存在

### 易错点 7：MemorySearch 全量加载到内存

**现象**：聊天记录多了以后，搜索卡顿，内存飙升。

**根因**：
```kotlin
// ❌ 把所有消息加载到 JVM，再用 Kotlin 做 contains 过滤
val allMessages = chatDao.getImportantMessages(sessionId) +
                  chatDao.getSessionMessages(sessionId, petType)
val result = allMessages.filter { it.content.contains(query) }
```

**正确做法**——在 SQLite 层完成过滤：
```kotlin
// ✅ 数据库 LIKE 查询，只返回匹配的结果
@Query("""
    SELECT * FROM chat_history
    WHERE sessionId = :sessionId
      AND (isImportant = 1 OR content LIKE '%' || :query || '%')
    ORDER BY timestamp DESC LIMIT 15
""")
suspend fun searchMessagesByKeyword(sessionId: String, query: String, limit: Int): List<ChatEntity>
```

**性能对比**：1000 条消息，SQLite LIKE 查询约 5ms，全量加载 + Kotlin 过滤约 50-100ms，且额外占用数十 KB 堆内存。

### 易错点 8：Android 13+ 通知权限

**现象**：ReminderWorker 触发后，手机没有弹出通知。

**根因**：Android 13 (API 33) 起，发送通知需要运行时权限 `POST_NOTIFICATIONS`。Worker 中调用 `notificationManager.notify()` 会被系统静默拦截，不抛异常，不报错——用户什么都看不到。

**解决方案**：
- 在 App 启动或首次设置提醒时，用 `ActivityResultContracts.RequestPermission` 请求通知权限
- `ReminderWorker` 中检查权限状态，给出降级处理

---

## 文件清单

| 文件 | 角色 |
|------|------|
| `model/ApiModels.kt` | API 数据模型：Tool, ToolCall, ToolCallDelta, StreamEvent 等 |
| `model/ChatModels.kt` | UI 数据模型：ChatMessage 扩展 ToolCallInfo |
| `model/ChatUiState.kt` | UI 状态：ChatUiState.Ready 扩展 agentStatus |
| `data/repository/ChatApiService.kt` | SSE 流解析：`makeAgentStreamingRequest()` 返回 `Flow<StreamEvent>` |
| `data/repository/ToolCallAccumulator.kt` | 增量 tool_call 累加器 |
| `data/repository/ChatRepository.kt` | **Agent Loop 核心**：`getPetAgentResponse()` + `AgentStreamListener` 接口 |
| `data/tools/Tool.kt` | Tool 接口 + ToolResult |
| `data/tools/ToolRegistry.kt` | 工具注册中心，Hilt 多绑定 |
| `data/tools/NoteTool.kt` | 笔记 CRUD 工具 |
| `data/tools/ReminderTool.kt` | 定时提醒工具 |
| `data/tools/MemorySearchTool.kt` | 记忆搜索工具（SQLite LIKE） |
| `data/entity/ReminderEntity.kt` | 提醒 Room 实体 |
| `data/dao/ReminderDao.kt` | 提醒 DAO |
| `data/dao/ChatDao.kt` | 新增 `searchMessagesByKeyword()` 数据库搜索 |
| `data/ChatDatabase.kt` | 版本 8→9，新增 ReminderEntity |
| `di/ToolModule.kt` | Hilt @Binds @IntoSet 注入所有工具 |
| `di/DatabaseModule.kt` | 新增 provideReminderDao |
| `service/ReminderWorker.kt` | @HiltWorker 提醒通知 |
| `ui/chat/PetChatViewModel.kt` | ViewModel：`sendMessageAgent()` + AgentStreamListener 实现 |
| `ui/chat/ChatComponents.kt` | UI：ChatBubble 工具状态判断 + ToolStatusBubble |

---

## 面试可能会问的问题

**Q: Agent Loop 的最大迭代次数为什么是 5？**

A: 这是一个经验值。对于笔记、提醒这类简单工具，通常 1-2 轮就能完成。5 轮是一个安全上限，既允许 LLM 在工具失败后重试修正，又不会让用户等太久。到达上限后强制终结（注入 system 消息 + 去掉 tools）。

**Q: 为什么不把 Tool 的执行放在 Worker 中异步执行？**

A: 对于笔记 CRUD、记忆搜索这类毫秒级操作，同步执行（在 `withContext(Dispatchers.IO)` 中）就能满足需求。只有 Reminder 的长时间延迟通知才用了 WorkManager。如果未来加入耗时操作（如下载文件），可以扩展为异步工具。

**Q: 为什么工具状态气泡不存数据库？**

A: 工具状态是瞬态的 UI 展示，与业务数据无关。存入数据库会增加存储开销、污染聊天历史查询结果、增加迁移负担。参考 ChatGPT 的做法——"Searching the web..."只在当前对话中可见，刷新后消失。

**Q: 如何新增一个工具？**

A: 三步：1) 实现 `Tool` 接口，写 `execute()` 方法；2) 在 `ToolModule` 中加 `@Binds @IntoSet`；3) 如果涉及新表，更新 `ChatDatabase` 和 `DatabaseModule`。`ToolRegistry` 自动发现新工具，无需修改。
