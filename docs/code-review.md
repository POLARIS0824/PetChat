# PetChat 代码审查报告

> 审查日期：2026-05-19
> 审查范围：全部 40 个 Kotlin 源文件
> 分支：refactor

---

## 目录

1. [BUG — 需要立即修复](#1-bug--需要立即修复)
2. [严重架构问题](#2-严重架构问题)
3. [数据层问题](#3-数据层问题)
4. [UI 层问题](#4-ui-层问题)
5. [ViewModel 问题](#5-viewmodel-问题)
6. [代码异味 — 跨文件](#6-代码异味--跨文件)
7. [优先修复建议](#7-优先修复建议)

---

## 1. BUG — 需要立即修复

### 1.1 `getAllSessions()` SQL 聚合错误

**文件**：`data/ChatDao.kt:88-93`

```sql
SELECT ch.sessionId, ch.petType, ch.content as lastMessage, MAX(ch.timestamp) as timestamp
FROM chat_history ch
GROUP BY ch.sessionId
ORDER BY timestamp DESC
```

**问题**：`GROUP BY ch.sessionId` 将同一会话的所有消息分组，`MAX(ch.timestamp)` 正确取到最新时间戳，但 `ch.content` 既不在 `GROUP BY` 中也不被聚合函数包裹。SQLite 标准规定，这种情况下 `content` 的值是**该组中任意一行**的值，不保证是时间戳最大的那一行。

**后果**：会话列表中显示的"最后一条消息"可能是一条随机的旧消息，而非用户真正看到的最新消息。

**修复方向**：使用子查询或窗口函数获取每个会话中 `timestamp` 最大的那条消息的 `content`。

---

### 1.2 SSE 流式解析 `[DONE]` 信号永远不会匹配

**文件**：`data/repository/ChatApiService.kt:127-134`

```kotlin
while (!source.exhausted()) {
    val line = source.readUtf8Line() ?: break
    if (line.isEmpty()) continue
    if (line == "[DONE]") {        // <-- 这行永远不会为 true
        completed = true
        listener.onComplete()
        break
    }
    if (line.startsWith("data: ")) {
        val jsonData = line.substring(6)
        // ...
    }
}
```

**问题**：根据 SSE（Server-Sent Events）协议，流结束信号的格式是 `data: [DONE]`，而不是裸 `[DONE]`。当前代码先检查 `line == "[DONE]"`（永远不匹配），然后检查 `line.startsWith("data: ")`。当收到 `data: [DONE]` 时，`jsonData` 变量值为 `[DONE]`，传入 `json.decodeFromString()` 后会抛出 JSON 解析异常。该异常被 `catch` 块静默捕获并记录日志。

**后果**：流的结束依赖 `while` 循环自然结束（`source.exhausted()` 返回 true）后 `if (!completed) listener.onComplete()` 的 fallback 逻辑。虽然功能上勉强能用，但每次流结束都会产生一个无意义的异常日志，且如果 fallback 逻辑有变动就会导致 `onComplete()` 不被调用。

**修复方向**：将 `if (line == "[DONE]")` 改为：

```kotlin
if (line.startsWith("data: ")) {
    val jsonData = line.substring(6)
    if (jsonData == "[DONE]") {
        completed = true
        listener.onComplete()
        break
    }
    // ... 正常解析 JSON
}
```

---

### 1.3 `analyzeChats()` 假设所有未处理消息属于同一宠物

**文件**：`data/repository/ChatRepository.kt:145-192`

```kotlin
suspend fun analyzeChats() {
    val unprocessedChats = chatDao.getUnprocessedChats()
    if (unprocessedChats.size < 10) return
    // ...
    val analysisEntity = ChatAnalysisEntity(
        petType = unprocessedChats.first().petType,  // <-- 只取第一条的 petType
        // ...
    )
    chatDao.insertAnalysis(analysisEntity)
    chatDao.update(unprocessedChats.map { it.copy(isProcessed = true) })
}
```

**问题**：`getUnprocessedChats()` 返回所有 `isProcessed = 0` 的消息，不限 petType。当用户在不同宠物之间切换时，未处理消息可能混合了多个宠物的对话。代码用 `unprocessedChats.first().petType` 取第一条消息的 petType 作为分析结果的 petType，但分析内容实际上包含了所有宠物的对话。

**后果**：分析结果（用户画像、偏好、互动模式）会被错误地归因到某一个宠物，且所有被标记为已处理的消息（无论属于哪个宠物）都不会再参与后续分析。

**修复方向**：按 `petType` 分组后分别分析，或在 `getUnprocessedChats()` 查询中加上 `WHERE petType = :petType` 过滤条件。

---

### 1.4 `PetTypes.HAMSTER` 的 Prompt 描述的是一只猫

**文件**：`data/repository/PromptConfig.kt:66-67`

```kotlin
PetTypes.HAMSTER to """
    你现在是一只名叫团绒的小猫咪，用第一视角进行真实自然的对话。你的品种是银渐层...
```

**问题**：`PetTypes.HAMSTER`（仓鼠）的系统 prompt 描述的是一只"银渐层小猫咪"，与枚举名称完全矛盾。

**后果**：这是一个设计意图问题。如果 HAMSTER 确实应该是一只猫，那枚举名称应该改为 `KITTEN` 或 `CAT2`；如果 HAMSTER 应该是仓鼠，那 prompt 内容需要完全重写。

---

### 1.5 `DeepseekRequest` 默认模型与 BuildConfig 不一致

**文件**：`model/ApiModels.kt:7` vs `app/build.gradle.kts`

```kotlin
// ApiModels.kt
data class DeepseekRequest(
    val model: String = "deepseek-r1",  // <-- 默认值是 deepseek-r1
    // ...
)

// build.gradle.kts
buildConfigField("String", "PETCHAT_MODEL", "\"deepseek-v3\"")  // <-- 配置的是 deepseek-v3
```

**问题**：`DeepseekRequest` 的 `model` 字段默认值是 `"deepseek-r1"`，但项目配置的模型是 `"deepseek-v3"`。虽然 `ChatRepository` 在构造请求时显式传入了 `model = model`（来自 BuildConfig），但如果有任何地方直接构造 `DeepseekRequest` 而不传 `model` 参数，就会使用错误的模型。

**后果**：目前 `ChatRepository` 的调用是安全的，但这是一个隐患。如果未来有人直接使用 `DeepseekRequest(messages = ...)` 构造请求而不传 model，会静默调用错误的模型。

**修复方向**：将 `DeepseekRequest` 的默认值改为 `BuildConfig.PETCHAT_MODEL`，或移除默认值强制显式传入。

---

### 1.6 `PetChatViewModel.onContent()` 中的协程泄漏

**文件**：`ui/chat/PetChatViewModel.kt:126-143`

```kotlin
override fun onContent(content: String) {
    responseBuffer.append(content)
    val updatedMessage = petMessage.copy(content = responseBuffer.toString())
    updateReady { state ->
        state.copy(
            streamingMessage = updatedMessage,
            chatHistory = state.chatHistory.dropLast(1) + updatedMessage,
            shouldScrollToBottom = false
        )
    }
    viewModelScope.launch {           // <-- 每次 onContent 回调都创建新协程
        delay(50)
        updateReady { it.copy(shouldScrollToBottom = true) }
    }
}
```

**问题**：`onContent()` 在流式响应过程中会被调用**数百次**（每次收到一小段文本就调用一次）。每次调用都通过 `viewModelScope.launch` 创建一个新的协程来延迟 50ms 后设置 `shouldScrollToBottom = true`。这意味着一次流式响应可能同时存在数百个活跃的延迟协程。

**后果**：
- 协程泄漏：大量短生命周期的协程占用 dispatcher 资源
- 竞态条件：多个协程同时修改 `shouldScrollToBottom`，导致滚动行为不可预测
- 如果 `onContent()` 被调用的速度快于协程执行的速度，会有大量协程排队等待执行

**修复方向**：使用单一的 `Job` 来管理滚动延迟，每次 `onContent()` 取消前一个 Job 并启动新的：

```kotlin
private var scrollJob: Job? = null

override fun onContent(content: String) {
    // ... 更新消息内容 ...
    scrollJob?.cancel()
    scrollJob = viewModelScope.launch {
        delay(50)
        updateReady { it.copy(shouldScrollToBottom = true) }
    }
}
```

---

### 1.7 `ChatScreen` 中 `getChatHistory()` 的副作用导致 `animateScrollToItem(-1)`

**文件**：`ui/chat/ChatScreen.kt:170-176`

```kotlin
onSendClick = {
    if (message.isNotEmpty()) {
        viewModel.sendMessage(message)
        message = ""
        coroutineScope.launch {
            listState.animateScrollToItem(viewModel.getChatHistory(petType).size - 1)
        }
    }
}
```

**文件**：`ui/chat/PetChatViewModel.kt:214-222`

```kotlin
fun getChatHistory(petType: PetTypes): List<ChatMessage> {
    val state = readyState()
    return if (state.currentPetType == petType) {
        state.chatHistory
    } else {
        selectPetType(petType)   // <-- 副作用：触发数据加载
        emptyList()              // <-- 返回空列表
    }
}
```

**问题**：`getChatHistory()` 是一个 getter 函数，但当 `petType` 不匹配时会触发 `selectPetType()` 副作用并返回空列表。在 `onSendClick` 中，`viewModel.sendMessage(message)` 刚刚添加了用户消息到聊天记录，但 `getChatHistory(petType)` 读取的是 `readyState()` 的快照。如果此时 `currentPetType` 因为某种原因不匹配（虽然概率较低），返回空列表会导致 `animateScrollToItem(-1)` 抛出 `IllegalArgumentException`。

**修复方向**：`getChatHistory()` 不应有副作用。滚动逻辑应直接使用 `state.chatHistory.size - 1` 而非重新查询。

---

### 1.8 `PetGreetingWorker` 始终使用 `PetTypes.CAT`

**文件**：`service/PetGreetingWorker.kt:73-79`

```kotlin
override suspend fun doWork(): Result {
    return try {
        val greeting = try {
            repository.getPetResponse(
                PetTypes.CAT,    // <-- 硬编码为 CAT
                "生成一句简短的问候语，表达对主人的思念或关心"
            )
```

**问题**：Worker 始终使用 `PetTypes.CAT` 生成问候语，完全忽略用户实际选择的宠物。Worker 没有访问用户偏好的机制。

**后果**：无论用户选择的是大白（狗）、团绒（猫/仓鼠）还是豆豆（柴犬），问候通知始终以布丁（猫）的口吻发送。

**修复方向**：将用户的宠物偏好存储在 SharedPreferences 中，Worker 启动时读取。

---

## 2. 严重架构问题

### 2.1 `fallbackToDestructiveMigration()` — 每次 Schema 变更销毁全部用户数据

**文件**：`di/DatabaseModule.kt:22-26`

```kotlin
fun provideDatabase(@ApplicationContext context: Context): ChatDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        ChatDatabase::class.java,
        "chat_database"
    ).fallbackToDestructiveMigration().build()
}
```

**文件**：`data/ChatDatabase.kt`

```kotlin
@Database(entities = [...], version = 5, exportSchema = false)
```

**问题**：
- 数据库已经到 version 5，但项目中没有任何 `Migration` 对象
- `fallbackToDestructiveMigration()` 会在每次数据库版本号变化时**删除所有表并重建**，即用户的所有聊天记录、笔记、分析数据全部丢失
- `exportSchema = false` 意味着 Room 不会生成 schema JSON 文件，无法用于迁移验证

**后果**：每次发布包含数据库 schema 变更的新版本，用户升级后所有数据消失。在开发阶段可以接受，但在生产环境中是不可接受的数据丢失。

**修复方向**：
1. 启用 `exportSchema = true`
2. 为每次 schema 变更编写显式的 `Migration(fromVersion, toVersion)`
3. 移除 `fallbackToDestructiveMigration()`
4. 仅在开发阶段保留破坏性迁移（通过 `BuildConfig.DEBUG` 判断）

---

### 2.2 `ChatRepository` — God Class（309 行，6+ 职责）

**文件**：`data/repository/ChatRepository.kt`

该类承担了以下至少 6 个独立职责：

| 职责 | 方法 | 行数 |
|------|------|------|
| Prompt 构建 | `getEnhancedPrompt()` | 34-51 |
| 消息拼接 | `buildMessages()` | 57-82 |
| API 调用 | `getPetResponse()`, `getPetResponseStreaming()`, `getPetResponseWithPictureInfoStreaming()` | 88-139 |
| 聊天分析 | `analyzeChats()` | 145-192 |
| 持久化与摘要 | `saveChatMessage()`, `summarizeConversation()`, `isMessageImportant()` | 198-248 |
| 图片信息提取 | `extractPictureInfo()`, `consumeLastPictureInfo()` | 264-287 |
| 会话代理 | `createNewSession()`, `setCurrentSessionId()`, `getSessionMessages()`, `getAllSessions()` | 293-306 |

**后果**：类的职责过多导致代码难以理解、测试和维护。修改任何一个功能都可能影响其他功能。

**修复方向**：
- 将 Prompt 构建提取为 `PromptBuilder`
- 将聊天分析提取为 `ChatAnalysisUseCase`
- 将会话管理代理方法移除（调用方直接使用 `SessionManager`）
- 将图片信息提取提取为 `PictureInfoParser`

---

### 2.3 `ChatDao` — God DAO（4 种实体类型）

**文件**：`data/ChatDao.kt`

一个 DAO 接口处理了 4 种完全不同的实体：
- `chat_history` 表（`ChatEntity`）— 聊天消息
- `chat_analysis` 表（`ChatAnalysisEntity`）— 聊天分析
- `notes` 表（`NoteEntity`）— 用户笔记
- 会话投影（`SessionEntity`）— 会话列表

**后果**：违反单一职责原则。修改笔记相关的查询需要在处理聊天消息的同一个 DAO 中操作，增加了出错风险。

**修复方向**：拆分为 `ChatDao`、`AnalysisDao`、`NotesDao`。

---

### 2.4 数据库无索引

**文件**：`data/ChatEntity.kt`、`data/ChatAnalysisEntity.kt`、`data/NoteEntity.kt`

三个 Entity 均未声明任何 `@Index` 注解，但 DAO 中有大量查询使用了 `WHERE` 和 `GROUP BY` 子句：

| Entity | 被查询过滤的列 | 查询方法 |
|--------|---------------|----------|
| `ChatEntity` | `isProcessed` | `getUnprocessedChats()`, `getUnprocessedChatsCount()` |
| `ChatEntity` | `sessionId`, `petType` | `getSessionMessages()` |
| `ChatEntity` | `petType` | `getMessagesByPetType()` |
| `ChatEntity` | `sessionId` | `getAllSessions()` (GROUP BY) |
| `ChatAnalysisEntity` | `petType`, `timestamp` | `getLatestAnalysis()` |
| `NoteEntity` | `petType` | `getNotesByType()` |

**后果**：所有这些查询都会执行全表扫描。当聊天记录增长到数千条时，查询性能会显著下降。

**修复方向**：

```kotlin
@Entity(
    tableName = "chat_history",
    indices = [
        Index("petType"),
        Index("sessionId"),
        Index("isProcessed"),
        Index("sessionId", "petType")
    ]
)
```

---

## 3. 数据层问题

### 3.1 `ChatEntity` 中 `role` 与 `isFromUser` 冗余

**文件**：`data/ChatEntity.kt:20,36`

```kotlin
val isFromUser: Boolean,
// ...
val role: String = if (isFromUser) "user" else "assistant",
```

**问题**：`role` 的默认值由 `isFromUser` 决定，但 `role` 可以被独立设置为其他值（如 `"system"`，在 `summarizeConversation()` 中使用）。这意味着 `isFromUser = true` 但 `role = "system"` 是可能出现的组合，造成数据不一致。

**修复方向**：移除 `isFromUser` 字段，统一使用 `role` 作为唯一的消息角色标识。在需要判断"是否来自用户"的地方，使用 `role == "user"` 替代。

---

### 3.2 `sessionId` 默认空字符串

**文件**：`data/ChatEntity.kt:33`

```kotlin
val sessionId: String = "",
```

**问题**：默认值为空字符串 `""`。`getAllSessions()` 的 `GROUP BY sessionId` 会将所有空字符串的 sessionId 聚合为一组，导致一个名为"默认会话"的条目实际上包含了所有没有显式设置 sessionId 的消息。

**修复方向**：移除默认值，强制所有写入点显式传入 sessionId。

---

### 3.3 `petType` 存储为裸 `String`

**文件**：`data/ChatEntity.kt:23`、`data/ChatAnalysisEntity.kt`、`data/NoteEntity.kt`

```kotlin
val petType: String,  // 存储 PetTypes.CAT.name = "CAT"
```

**问题**：所有三个 Entity 的 `petType` 字段都是 `String` 类型。读取时需要手动将字符串转回枚举：

```kotlin
PetTypes.entries.firstOrNull { it.name == entity.petType } ?: PetTypes.CAT
```

如果枚举值被重命名（如 `DOG2` -> `DOG_DODOU`），数据库中存储的旧值 `"DOG2"` 将无法匹配，所有属于该宠物的数据都会静默回退到 `PetTypes.CAT`。

**修复方向**：使用 Room `TypeConverter` 将 `PetTypes` 枚举自动转换为字符串存储。

---

### 3.4 `buildMessages()` 消息交错逻辑假设完美交替

**文件**：`data/repository/ChatRepository.kt:66-78`

```kotlin
val processedMessages = recentMessages
    .distinctBy { "${it.role}:${it.content}" }
    .sortedBy { it.timestamp }
    .groupBy { it.isFromUser }

val userMessages = processedMessages[true] ?: listOf()
val assistantMessages = processedMessages[false] ?: listOf()

val maxIndex = maxOf(userMessages.size, assistantMessages.size)
for (i in 0 until maxIndex) {
    if (i < assistantMessages.size) messages.add(Message("assistant", assistantMessages[i].content))
    if (i < userMessages.size) messages.add(Message("user", userMessages[i].content))
}
```

**问题**：该逻辑将消息按 `isFromUser` 分为两组，然后按索引交错合并。这假设用户消息和助手消息是完美交替的（user, assistant, user, assistant...）。但在以下场景中假设不成立：
- 插入了摘要消息（`role = "system"`, `isFromUser = false`）
- 用户连续发了两条消息（网络延迟导致助手未回复时用户又发了一条）
- 消息被删除或部分清理

**后果**：交错后的消息顺序可能与实际对话顺序不同，导致 AI 收到的上下文混乱，产生不连贯的回复。

**修复方向**：直接按 `timestamp` 排序后转换，不要分组交错：

```kotlin
val messages = recentMessages
    .sortedBy { it.timestamp }
    .map { Message(role = it.role, content = it.content) }
```

---

### 3.5 `summarizeConversation()` 与 `analyzeChats()` 缺乏事务保护

**文件**：`data/repository/ChatRepository.kt:219-248`

```kotlin
private suspend fun summarizeConversation() {
    // ...
    chatDao.insert(summaryEntity)                          // 步骤 1：插入摘要
    chatDao.update(messages.map { it.copy(isProcessed = true) })  // 步骤 2：标记已处理
}
```

**文件**：`data/repository/ChatRepository.kt:186-188`

```kotlin
chatDao.insertAnalysis(analysisEntity)                    // 步骤 1：插入分析
chatDao.update(unprocessedChats.map { it.copy(isProcessed = true) })  // 步骤 2：标记已处理
```

**问题**：两个方法都执行两步数据库操作（插入 + 更新），但没有包裹在 `@Transaction` 中。如果步骤 1 成功但步骤 2 失败（如进程被杀、OOM），会导致：
- 摘要/分析已保存，但消息仍标记为未处理
- 下次触发时会重复分析/摘要同一批消息

**修复方向**：在 DAO 中添加事务方法：

```kotlin
@Transaction
suspend fun insertAnalysisAndMarkProcessed(analysis: ChatAnalysisEntity, chatIds: List<Long>) {
    insertAnalysis(analysis)
    update(chatIds.map { /* mark processed */ })
}
```

---

### 3.6 `ChatApiService.makeStreamingApiRequest()` 无协程取消支持

**文件**：`data/repository/ChatApiService.kt:92`

```kotlin
fun makeStreamingApiRequest(request: DeepseekRequest, listener: StreamResponseListener) {
```

**问题**：这是一个普通函数（非 `suspend`），使用 OkHttp 的 `enqueue` 异步回调。与 `makeApiRequest()` 不同，它没有通过 `suspendCancellableCoroutine` 包装，因此：
- 调用方的协程取消时，HTTP 请求不会被取消
- 用户离开聊天界面后，流式响应仍会继续接收和处理

**修复方向**：使用 `suspendCancellableCoroutine` 包装，并在 `invokeOnCancellation` 中调用 `call.cancel()`。

---

### 3.7 `extractPictureInfo()` 使用字符串索引解析

**文件**：`data/repository/ChatRepository.kt:272-287`

```kotlin
private fun extractPictureInfo(response: String): Pair<String, PictureInfo> {
    val systemNoteStart = response.indexOf("<system_note>")
    val systemNoteEnd = response.indexOf("</system_note>")
    // ...
    val jsonStr = response.substring(systemNoteStart + 13, systemNoteEnd)
```

**问题**：通过 `indexOf` 查找 `<system_note>` 和 `</system_note>` 标签来提取 JSON。如果 AI 回复中包含这些标签的代码块或引用文本（如"你可以使用 `<system_note>` 标签"），解析会错误匹配。

**修复方向**：使用正则表达式匹配，或改用更结构化的响应格式（如在 JSON 的特定字段中返回图片信息）。

---

### 3.8 `isMessageImportant()` 判断逻辑过于简单

**文件**：`data/repository/ChatRepository.kt:213-217`

```kotlin
private fun isMessageImportant(content: String): Boolean {
    return content.contains("?") || content.contains("!") ||
            content.length > 50 || content.contains("喜欢") ||
            content.contains("不喜欢") || content.contains("想要")
}
```

**问题**：
- 包含 `?` 或 `!` 就标记为重要，但日常对话中大量消息包含这些标点
- 长度超过 50 字符就标记为重要，但一段无关紧要的描述也可能超过 50 字
- 只检查了 3 个中文关键词，覆盖面极窄
- 英文消息完全不受关键词检查影响

**后果**：大量不重要的消息被标记为重要，导致摘要功能被不重要的内容干扰。

---

### 3.9 `NotesRepository.getAllNotes()` 的 N+1 查询

**文件**：`data/repository/NotesRepository.kt`

```kotlin
suspend fun getAllNotes(): List<NoteEntity> {
    val allNotes = mutableListOf<NoteEntity>()
    PetTypes.entries.forEach { petType ->
        allNotes.addAll(chatDao.getNotesByType(petType.name))
    }
    return allNotes.sortedByDescending { it.timestamp }
}
```

**问题**：为每种 `PetTypes` 发一次数据库查询（4 次），然后在内存中合并排序。一个简单的 `SELECT * FROM notes ORDER BY timestamp DESC` 就能完成。

---

### 3.10 `SessionManager` 会话 ID 不持久化

**文件**：`data/repository/SessionManager.kt`

```kotlin
@Volatile
var currentSessionId: String = ""
    private set
```

**问题**：`currentSessionId` 仅存储在内存中。当应用进程被系统杀死后重启，会话 ID 丢失，生成新的 UUID。用户之前的"当前会话"上下文消失。

---

## 4. UI 层问题

### 4.1 `ChatScreen` 中三个 `LaunchedEffect` 竞争滚动控制

**文件**：`ui/chat/ChatScreen.kt`

三个独立的 `LaunchedEffect` 块各自尝试控制 `LazyColumn` 的滚动位置：

1. **第 56-61 行**：`LaunchedEffect(petType)` — 宠物切换时滚动到底部
2. **第 135-155 行**：`LaunchedEffect(state.shouldScrollToBottom, state.chatHistory.size)` — 消息数变化或滚动标志变化时滚动
3. **第 157-164 行**：`LaunchedEffect(state.streamingMessage)` — 流式消息更新时滚动

**问题**：这三个效果在流式响应期间会同时激活，各自调用 `animateScrollToItem()` 或 `scrollBy()`。多个动画同时作用于同一个 `LazyListState` 会导致：
- 滚动位置抖动（一个效果向下滚动，另一个效果也向下滚动但速度不同）
- 动画冲突（两个 `animateScrollToItem` 同时执行）
- 用户无法手动滚动查看历史消息（随时被自动滚动拉回底部）

**修复方向**：统一为一个滚动控制器，使用 `Channel` 或 `SharedFlow` 发送滚动事件，由单一的 `LaunchedEffect` 处理。

---

### 4.2 `LazyColumn` 的 `key` 使用 `hashCode()`

**文件**：`ui/chat/ChatScreen.kt:120`

```kotlin
items(
    items = state.chatHistory,
    key = { it.hashCode() }
)
```

**问题**：`ChatMessage.hashCode()` 基于所有字段（`content`, `isFromUser`, `petType`, `timestamp`, `role`）自动生成。
- 如果两条消息内容相同但时间戳不同（AI 回复相同内容很常见），它们的 `hashCode` 不同，Compose 会认为是不同的 item，无法复用
- 如果 `hashCode` 碰撞（概率低但存在），两条不同的消息会被 Compose 视为同一条，导致 UI 错乱
- `hashCode` 不是稳定的标识符，重组前后可能变化

**修复方向**：为 `ChatMessage` 添加一个稳定的唯一 ID（如 UUID 或数据库自增 ID）。

---

### 4.3 `PetCards.kt` 中 `RenderEffect` 每帧重建

**文件**：`ui/cards/PetCards.kt:137-139`

```kotlin
graphicsLayer {
    renderEffect = RenderEffect.createBlurEffect(
        blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP
    )
}
```

**问题**：`graphicsLayer` 的 lambda 在每一帧绘制时都会执行。`RenderEffect.createBlurEffect()` 每次调用都会创建一个新的 `RenderEffect` 对象。在拖拽动画期间，这意味着每秒创建 60 个 `RenderEffect` 对象，造成不必要的内存分配和 GC 压力。

**修复方向**：当 `blurRadiusPx` 为 0 时跳过 `RenderEffect` 创建，或使用 `remember` 缓存 `RenderEffect` 对象。

---

### 4.4 `SocialViewModel.likePost()` 点赞计数可变为负数

**文件**：`ui/social/SocialViewModel.kt`

```kotlin
fun likePost(postId: String) {
    val index = _posts.indexOfFirst { it.id == postId }
    if (index != -1) {
        val post = _posts[index]
        _posts[index] = post.copy(
            isLiked = !post.isLiked,
            likeCount = if (post.isLiked) post.likeCount - 1 else post.likeCount + 1
        )
    }
}
```

**问题**：当 `post.isLiked = true` 且 `post.likeCount = 0` 时（数据不一致），取消点赞会使 `likeCount` 变为 -1。没有下限检查。

---

### 4.5 多个空 `onClick` 处理器

以下位置的按钮/菜单项看起来可交互但点击后无任何响应：

| 文件 | 行号 | 元素 |
|------|------|------|
| `DrawerComponents.kt` | 68 | 账户信息 |
| `DrawerComponents.kt` | 69 | 密码设置 |
| `DrawerComponents.kt` | 70 | 偏好设置 |
| `DrawerComponents.kt` | 71 | 系统设置 |
| `DrawerComponents.kt` | 76 | 退出登录 |
| `PetCards.kt` | 215 | 删除按钮 |
| `SocialScreen.kt` | 172 | 评论按钮 |

**修复方向**：要么实现功能，要么禁用按钮（`enabled = false`），避免用户困惑。

---

### 4.6 `NotesScreen.kt` 大段注释掉的死代码

**文件**：`ui/notes/NotesScreen.kt:195-209`

包含一个被注释掉的删除按钮实现。死代码应被删除，需要时可通过 git 历史找回。

---

## 5. ViewModel 问题

### 5.1 `SocialViewModel` 和 `CardsViewModel` 使用 `mutableStateListOf`

**文件**：`ui/social/SocialViewModel.kt:16-17`、`ui/cards/CardsViewModel.kt:13`

```kotlin
private val _posts = mutableStateListOf<SocialPost>()
```

**问题**：`mutableStateListOf` 是 Compose 运行时的状态类型（`androidx.compose.runtime`），不适合在 ViewModel 中使用：
- 耦合了 ViewModel 到 Compose 运行时，无法在不依赖 Compose 的单元测试中测试
- 不能使用 Kotlin Flow 的操作符（`map`, `filter`, `combine` 等）
- 不符合 Google 推荐的 `StateFlow` 模式

**修复方向**：改为 `MutableStateFlow<List<T>>`：

```kotlin
private val _posts = MutableStateFlow<List<SocialPost>>(emptyList())
val posts: StateFlow<List<SocialPost>> = _posts.asStateFlow()
```

---

### 5.2 ViewModel 缺乏统一的错误处理

所有 ViewModel 在 `viewModelScope.launch` 中调用 repository 方法时都没有 `try-catch`：

| ViewModel | 方法 |
|-----------|------|
| `PetChatViewModel` | `loadChatHistory()`, `sendMessageStreaming()` 的部分路径 |
| `NotesViewModel` | `loadNotes()`, `addNote()`, `deleteNote()`, `updateNote()` |
| `SocialViewModel` | 所有方法 |
| `CardsViewModel` | 所有方法 |

**后果**：如果 repository 抛出异常，协程会静默失败（异常传播到 `CoroutineExceptionHandler`，默认只打印日志），UI 不会显示任何错误信息。

---

### 5.3 `PetChatViewModel.getChatHistory()` 有副作用

**文件**：`ui/chat/PetChatViewModel.kt:214-222`

```kotlin
fun getChatHistory(petType: PetTypes): List<ChatMessage> {
    val state = readyState()
    return if (state.currentPetType == petType) {
        state.chatHistory
    } else {
        selectPetType(petType)   // <-- getter 中触发了数据加载
        emptyList()
    }
}
```

**问题**：一个名为 `get` 的函数应该只读取数据，但它在条件不满足时会触发 `selectPetType()` 改变状态并加载数据。这是命令-查询分离原则（CQS）的违反。

---

### 5.4 `NotesViewModel.loadNotes()` 应为 private

**文件**：`ui/notes/NotesViewModel.kt:34`

```kotlin
fun loadNotes() {  // <-- public 但只在内部调用
    viewModelScope.launch { ... }
}
```

**问题**：该函数被声明为 `public`，但只在 ViewModel 内部的 `addNote()`、`deleteNote()`、`updateNote()`、`setFilter()` 中调用。暴露为 public 会让外部调用者误以为应该手动调用它来刷新数据。

---

### 5.5 `SessionListScreen` 与 `ChatScreen` 共享 ViewModel

**文件**：`ui/session/SessionListScreen.kt:40`

```kotlin
fun SessionListScreen(
    viewModel: PetChatViewModel,  // <-- 与 ChatScreen 使用同一个 ViewModel
    // ...
)
```

**问题**：`SessionListScreen` 直接接收 `PetChatViewModel` 实例，与 `ChatScreen` 共享。这意味着：
- 在会话列表中选择一个会话会立即改变聊天界面的状态
- 如果用户在会话列表和聊天界面之间快速切换，可能出现状态竞争
- 违反了"每个屏幕应有独立 ViewModel 或作用域"的原则

---

### 5.6 `NotesViewModel` 每次操作后全量重新查询

**文件**：`ui/notes/NotesViewModel.kt`

```kotlin
fun addNote(content: String, petType: PetTypes) {
    viewModelScope.launch {
        repository.insertNote(NoteEntity(content = content, petType = petType.name))
        loadNotes()  // <-- 插入后重新查询全部笔记
    }
}

fun deleteNote(note: NoteEntity) {
    viewModelScope.launch {
        repository.deleteNote(note)
        loadNotes()  // <-- 删除后重新查询全部笔记
    }
}
```

**问题**：每次增删改操作后都调用 `loadNotes()` 重新查询全部数据。Room 支持返回 `Flow<List<NoteEntity>>` 的响应式查询，数据变化时自动通知 UI，无需手动刷新。

---

## 6. 代码异味 — 跨文件

### 6.1 `Color(255, 143, 45)` 硬编码出现 10+ 次

项目的主题橙色 `Color(255, 143, 45)` 在以下文件中以原始颜色值形式出现：

- `ui/components/PetAvatar.kt`
- `ui/chat/ChatComponents.kt`
- `ui/social/SocialScreen.kt`
- `ui/cards/PetCards.kt`
- `ui/notes/NotesScreen.kt`
- `ui/navigation/DrawerComponents.kt`
- 其他文件

**修复方向**：在 `ui/theme/Color.kt` 中定义 `val AccentOrange = Color(0xFFFF8F2D)` 并全局引用。

---

### 6.2 硬编码中文字符串

通知标题、宠物显示名称、问候消息、API prompt 等全部硬编码为中文字符串，未使用 `strings.xml` 资源：

| 位置 | 硬编码字符串 |
|------|-------------|
| `PetGreetingWorker.kt` | "来自宠物的问候"、"宠物问候"、"喵~ 想你了，主人！" |
| `DrawerComponents.kt` | "POLARIS"、"Unique Studio"、"退出登录" |
| `ChatScreen.kt` | "开始和宠物聊天吧！" |
| `PetChatViewModel.kt` | "抱歉，我现在有点累了，待会再聊吧。" |
| `PromptConfig.kt` | 所有宠物 prompt |

---

### 6.3 `SimpleDateFormat` 在 Composable 中每次重组创建

以下位置在函数体内创建 `SimpleDateFormat`，每次重组都会创建新实例：

| 文件 | 位置 |
|------|------|
| `ui/chat/ChatComponents.kt:111` | `ChatBubble` 中的时间格式化 |
| `ui/social/SocialScreen.kt:251-257` | `formatDate()` 函数 |
| `ui/session/SessionListScreen.kt:75` | `formatTime()` 函数 |

**修复方向**：使用 `remember { SimpleDateFormat(...) }` 缓存，或使用 `java.time.format.DateTimeFormatter`（线程安全且不可变）。

---

### 6.4 魔法数字

| 位置 | 数字 | 含义 |
|------|------|------|
| `ChatRepository.kt:29` | `3` | 上下文消息数量限制 |
| `ChatRepository.kt:147` | `10` | 触发聊天分析的阈值 |
| `ChatRepository.kt:210` | `20` | 触发对话摘要的阈值 |
| `ChatRepository.kt:214` | `50` | 判断消息重要性的长度阈值 |
| `ChatScreen.kt:58` | `300` | 滚动延迟毫秒数 |
| `PetChatViewModel.kt:123,140,167` | `50`, `100` | 各种延迟毫秒数 |

**修复方向**：提取为命名常量或配置项。

---

### 6.5 时间戳类型不一致

| 文件 | 使用的类型 |
|------|-----------|
| `ChatEntity.kt` | `Long` |
| `ChatMessage` | `Long` |
| `SessionInfo` | `Long` |
| `SocialModels.kt` | `java.util.Date` |

项目应统一使用 `java.time.Instant`（API 26+，项目 minSdk 31 满足要求）或 `Long`（epoch millis），不应混用。

---

### 6.6 `PetTypes` 命名问题

**文件**：`model/ChatModels.kt`

```kotlin
enum class PetTypes(val displayName: String) {
    CAT("布丁"),
    DOG("大白"),
    HAMSTER("团绒"),
    DOG2("豆豆"),
}
```

问题：
- `PetTypes` 应为 `PetType`（单数），每个枚举值代表一个宠物类型，不是类型的集合
- `DOG2` 是一个无意义的名称，应改为 `DOG_DODOU` 或 `SHIBA` 等描述性名称

---

### 6.7 `ChatUiState` 和 `NotesUiState` 缺少 Error 状态

**文件**：`model/ChatUiState.kt`、`model/NotesUiState.kt`

```kotlin
sealed interface ChatUiState {
    data object Loading : ChatUiState
    data class Ready(...) : ChatUiState
    // 没有 Error 状态
}
```

**问题**：当数据加载失败时，UI 无法优雅地显示错误信息。错误只能在 `Ready` 状态内部处理，或通过其他机制（如 Snackbar）显示，导致错误处理逻辑分散。

---

### 6.8 `NotesUiState` 直接依赖 Room Entity

**文件**：`model/NotesUiState.kt`

```kotlin
import com.example.chat.data.NoteEntity

sealed interface NotesUiState {
    data class Ready(
        val notes: List<NoteEntity> = emptyList(),  // <-- UI 层直接使用数据库实体
```

**问题**：UI 状态模型直接引用了 Room 数据库实体类。如果数据库 schema 变更（如重命名列），UI 层的代码也需要修改。应使用独立的 UI 模型（`data class NoteUiModel`）进行隔离。

---

### 6.9 资源 ID 泄漏到模型层

以下数据模型存储了 Android 资源 ID：

| 文件 | 字段 |
|------|------|
| `model/Pet.kt` | `imageRes: Int`, `initialRes: Int`, `finalRes: Int` |
| `model/SocialModels.kt` | `authorAvatar: Int` |

**问题**：Android 资源 ID 是 `Int` 类型，与普通整数无法区分。这耦合了模型层到 Android 框架，使模型无法在纯 JVM 单元测试中使用。

---

### 6.10 零测试覆盖率

项目中仅存在默认生成的测试文件：

- `test/.../ExampleUnitTest.kt` — 只有 `assertEquals(4, 2 + 2)`
- `androidTest/.../ExampleInstrumentedTest.kt` — 只有包名验证

没有针对 Repository、ViewModel、DAO 或 API Service 的任何测试。

---

### 6.11 `PetGreetingWorker` 调度设计缺陷

**文件**：`PetChatApplication.kt:22`、`service/PetGreetingWorker.kt:65-69`

```kotlin
// PetChatApplication.kt - 每次启动都调用
PetGreetingWorker.schedule(this, 9, 0)

// PetGreetingWorker.kt
workManager.enqueueUniquePeriodicWork(
    WORK_NAME,
    ExistingPeriodicWorkPolicy.REPLACE,  // <-- 每次都替换
    workRequest
)
```

**问题**：
1. `ExistingPeriodicWorkPolicy.REPLACE` 导致每次应用冷启动都重置 24 小时周期。如果用户在不同时间打开应用，Worker 的触发时间会不断漂移
2. `saveGreetingTime()` 将时间保存到 SharedPreferences，但从未读取。保存的数据是死数据
3. `FLAG_ACTIVITY_CLEAR_TASK` 在通知点击时销毁整个任务栈，用户正在进行的聊天会丢失

---

### 6.12 Manifest 中未使用的权限

**文件**：`AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

`PetGreetingWorker` 使用 `PeriodicWorkRequest`，不需要精确闹钟权限。该权限在 Android 14+ 需要特殊处理，声明但不使用会造成不必要的权限审查问题。

---

## 7. 优先修复建议

### P0 — 立即修复（影响正确性）

1. 修复 `getAllSessions()` SQL 聚合 bug
2. 修复 SSE `[DONE]` 解析逻辑
3. 修复 `analyzeChats()` petType 混合问题
4. 修复 `onContent()` 协程泄漏

### P1 — 短期修复（影响数据安全和稳定性）

5. 移除 `fallbackToDestructiveMigration()`，编写正式 Migration
6. 为 Entity 添加 `@Index` 注解
7. 修复 `PetGreetingWorker` 硬编码 petType
8. 统一 ViewModel 错误处理模式

### P2 — 中期重构（改善架构）

9. 拆分 `ChatRepository` 为多个职责单一的类
10. 拆分 `ChatDao` 为多个 DAO
11. 统一 ViewModel 状态管理模式（`StateFlow` 替代 `mutableStateListOf`）
12. 统一 `ChatScreen` 滚动管理逻辑
13. 为 `petType` 添加 Room TypeConverter

### P3 — 长期改进（代码质量）

14. 提取颜色常量和字符串资源
15. 统一时间戳类型
16. 重命名 `PetTypes` -> `PetType`，`DOG2` -> `DOG_DODOU`
17. 添加单元测试和集成测试
18. 移除死代码和未使用的权限
