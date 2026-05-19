# PetChat 项目架构与业务逻辑全解析

> 文档日期：2026-05-19
> 分支：refactor
> 适用于：全面学习和理解 PetChat 项目的架构设计、业务逻辑和完整调用流程

---

## 目录

1. [项目概览](#1-项目概览)
2. [技术栈与依赖](#2-技术栈与依赖)
3. [整体架构设计](#3-整体架构设计)
4. [源文件清单与职责](#4-源文件清单与职责)
5. [依赖注入体系](#5-依赖注入体系)
6. [数据层详解](#6-数据层详解)
7. [网络层详解](#7-网络层详解)
8. [Repository 层详解](#8-repository-层详解)
9. [ViewModel 层详解](#9-viewmodel-层详解)
10. [UI 层与导航体系](#10-ui-层与导航体系)
11. [核心业务流程：消息收发全流程](#11-核心业务流程消息收发全流程)
12. [智能分析流程](#12-智能分析流程)
13. [每日问候流程](#13-每日问候流程)
14. [会话管理机制](#14-会话管理机制)
15. [构建配置与 API 接入](#15-构建配置与-api-接入)
16. [附录：关键常量与配置](#16-附录关键常量与配置)

---

## 1. 项目概览

PetChat 是一款 Android 宠物聊天应用，用户可以与 4 只性格各异的虚拟宠物（猫、柴犬、萨摩耶、仓鼠）进行 AI 对话。后端使用阿里云 DashScope 兼容接口调用 DeepSeek-V3 模型，采用 SSE 流式传输实现打字机效果。

**核心特性：**
- 4 只宠物，各有独立人设和对话风格
- SSE 流式对话，实时打字效果
- 用户画像分析：自动分析聊天记录，提取偏好和行为模式
- 会话管理：支持多会话切换和历史查看
- 每日问候：WorkManager 定时推送宠物问候通知
- 便利贴：按宠物分类的笔记功能
- 名片夹：宠物卡片收藏展示
- 萌友圈：宠物社交动态（当前为本地模拟数据）

---

## 2. 技术栈与依赖

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.21 |
| UI 框架 | Jetpack Compose + Material3 | BOM 2024.12.01 |
| 导航 | Navigation3（实验性） | 1.0.0 |
| 网络 | OkHttp（原生，无 Retrofit） | 4.9.3 |
| JSON | kotlinx.serialization | 1.7.3 |
| 数据库 | Room | 2.6.1 |
| 依赖注入 | Hilt | 2.53.1 |
| 后台任务 | WorkManager | 2.10.0 |
| 异步 | Kotlin Coroutines + Flow | 1.7.3 |
| 系统栏 | Accompanist SystemUI Controller | 0.32.0 |

**编译配置：**
- `compileSdk = 36`，`minSdk = 31`，`targetSdk = 36`
- Java 17
- AGP 8.9.1，KSP 2.0.21-1.0.27

---

## 3. 整体架构设计

采用 **MVVM + Repository** 分层架构：

```
┌─────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                 │
│  ChatScreen / PetCards / NotesScreen / SocialScreen  │
│  SessionListScreen / SettingsScreen                  │
├─────────────────────────────────────────────────────┤
│                ViewModel Layer                       │
│  PetChatViewModel / CardsViewModel                   │
│  NotesViewModel / SocialViewModel                    │
├─────────────────────────────────────────────────────┤
│               Repository Layer                       │
│  ChatRepository / NotesRepository                    │
│  PromptBuilder / ChatAnalysisUseCase                 │
│  SessionManager / SettingsManager                    │
├────────────────────────────┬────────────────────────┤
│      Data Layer (Room)     │   Network Layer (OkHttp)│
│  ChatDatabase / ChatDao    │   ChatApiService        │
│  AnalysisDao / NotesDao    │   SSE Streaming         │
└────────────────────────────┴────────────────────────┘
```

**设计要点：**
- 单 Activity 架构，所有页面均为 Compose 函数
- Repository 为 Hilt `@Singleton`，集中管理数据操作
- 使用 `callbackFlow` 桥接 OkHttp 回调与 Kotlin Flow
- `StreamResponseListener` 接口封装流式回调
- SharedPreferences 存储轻量配置（会话 ID、API 设置）

---

## 4. 源文件清单与职责

### 4.1 应用入口

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | `@AndroidEntryPoint`，设置 edge-to-edge，调用 `PetChatApp()` |
| `PetChatApplication.kt` | `@HiltAndroidApp`，自定义 WorkManager 初始化，调度每日问候 |

### 4.2 数据层 — 数据库

| 文件 | 职责 |
|------|------|
| `ChatDatabase.kt` | Room 数据库，版本 8，3 张表，破坏性迁移 |
| `Converters.kt` | `PetType` 枚举与 String 的 TypeConverter |
| `dao/ChatDao.kt` | 聊天消息 CRUD、按会话查询、未处理消息统计 |
| `dao/AnalysisDao.kt` | 分析结果存取 |
| `dao/NotesDao.kt` | 便利贴 CRUD，按宠物类型过滤 |
| `entity/ChatEntity.kt` | 聊天消息实体 |
| `entity/ChatAnalysisEntity.kt` | 分析结果实体（摘要、偏好、行为模式） |
| `entity/NoteEntity.kt` | 便利贴实体 |

### 4.3 数据层 — Repository

| 文件 | 职责 |
|------|------|
| `ChatRepository.kt` | 核心仓库：API 调用、消息构建、持久化、触发分析 |
| `ChatApiService.kt` | OkHttp 客户端，SSE 流式请求 |
| `ChatAnalysisUseCase.kt` | 聊天分析和对话摘要逻辑 |
| `NotesRepository.kt` | 便利贴 CRUD 仓库 |
| `PromptBuilder.kt` | 构建系统提示词，注入用户画像 |
| `PromptConfig.kt` | 4 只宠物的静态人设提示词 |
| `SessionManager.kt` | 会话 ID 管理（SharedPreferences） |
| `SettingsManager.kt` | API 配置持久化（SharedPreferences） |
| `ApiConfig.kt` | API 配置数据类 |
| `PictureInfoParser.kt` | 从 AI 响应中提取 `<system_note>` 图片信息 |

### 4.4 Model 层

| 文件 | 职责 |
|------|------|
| `ApiModels.kt` | `DeepseekRequest`、`Message`、`DeepseekResponse`、`StreamResponseListener` |
| `ChatModels.kt` | `PetType` 枚举、`ChatMessage`、`PictureInfo` |
| `ChatUiState.kt` | 聊天 UI 状态密封接口（Loading/Ready/Error） |
| `NotesUiState.kt` | 便利贴 UI 状态密封接口 |
| `Pet.kt` | 宠物卡片数据类 |
| `SessionInfo.kt` | 会话元数据 |
| `SocialModels.kt` | 社交动态数据类 |

### 4.5 DI 层

| 文件 | 职责 |
|------|------|
| `di/DatabaseModule.kt` | Hilt 模块，提供 Database、DAO 实例 |

### 4.6 Service 层

| 文件 | 职责 |
|------|------|
| `service/PetGreetingWorker.kt` | WorkManager 每日问候 Worker |

### 4.7 UI 层

| 文件 | 职责 |
|------|------|
| `ui/app/PetChatApp.kt` | 根 Composable：Scaffold + Drawer + TopBar + BottomBar + NavDisplay |
| `ui/navigation/AppNavigation.kt` | `TopLevelBackStack` 自定义回退栈 |
| `ui/navigation/DrawerComponents.kt` | 侧边抽屉内容 |
| `ui/navigation/Routes.kt` | 路由定义（6 个路由） |
| `ui/chat/ChatScreen.kt` | 聊天界面 |
| `ui/chat/ChatComponents.kt` | 气泡、输入框、打字指示器、动画 |
| `ui/chat/PetChatViewModel.kt` | 主聊天 ViewModel |
| `ui/cards/PetCards.kt` | 宠物卡片列表，拖拽手势 |
| `ui/cards/CardsViewModel.kt` | 卡片 ViewModel |
| `ui/components/PetAvatar.kt` | 可复用宠物头像组件 |
| `ui/notes/NotesScreen.kt` | 便利贴界面（网格布局、筛选、增删改） |
| `ui/notes/NotesViewModel.kt` | 便利贴 ViewModel（响应式过滤） |
| `ui/session/SessionListScreen.kt` | 会话列表界面 |
| `ui/settings/SettingsScreen.kt` | API 设置界面 |
| `ui/social/SocialScreen.kt` | 萌友圈动态界面 |
| `ui/social/SocialViewModel.kt` | 萌友圈 ViewModel（本地模拟数据） |
| `ui/theme/Color.kt` | 颜色常量 |
| `ui/theme/Theme.kt` | 主题定义，支持动态取色 |
| `ui/theme/Type.kt` | 字体排版 |

---

## 5. 依赖注入体系

使用 Hilt 进行依赖注入，所有核心组件均为 `@Singleton`。

### 5.1 Hilt 模块（显式绑定）

`DatabaseModule` 提供以下绑定：

```
ChatDatabase  ──→  Room.databaseBuilder("chat_database")
ChatDao       ──→  database.chatDao()
AnalysisDao   ──→  database.analysisDao()
NotesDao      ──→  database.notesDao()
```

### 5.2 自动注入组件（@Inject constructor）

```
ChatApiService       @Singleton  ← SettingsManager
ChatRepository       @Singleton  ← ChatDao, ChatApiService, SessionManager,
                                   PromptBuilder, PictureInfoParser,
                                   ChatAnalysisUseCase, SettingsManager
ChatAnalysisUseCase  @Singleton  ← ChatDao, AnalysisDao, ChatApiService,
                                   PromptBuilder, ChatDatabase, SettingsManager
NotesRepository      @Singleton  ← NotesDao
PromptBuilder        @Singleton  ← AnalysisDao
PictureInfoParser    @Singleton  ← (无依赖)
SessionManager       @Singleton  ← @ApplicationContext, ChatDao
SettingsManager      @Singleton  ← @ApplicationContext
```

### 5.3 ViewModel 注入

```
PetChatViewModel  @HiltViewModel  ← ChatRepository, SessionManager, Application
CardsViewModel    @HiltViewModel  ← (无依赖)
NotesViewModel    @HiltViewModel  ← NotesRepository
SocialViewModel   @HiltViewModel  ← (无依赖)
```

### 5.4 Worker 注入

```
PetGreetingWorker  @HiltWorker  ← ChatRepository
```

### 5.5 依赖关系图

```
PetChatViewModel
  ├── ChatRepository
  │     ├── ChatDao
  │     ├── ChatApiService ← SettingsManager
  │     ├── SessionManager ← ChatDao
  │     ├── PromptBuilder ← AnalysisDao
  │     ├── PictureInfoParser
  │     ├── ChatAnalysisUseCase
  │     │     ├── ChatDao
  │     │     ├── AnalysisDao
  │     │     ├── ChatApiService
  │     │     ├── PromptBuilder
  │     │     ├── ChatDatabase
  │     │     └── SettingsManager
  │     └── SettingsManager
  └── SessionManager

NotesViewModel
  └── NotesRepository ← NotesDao
```

---

## 6. 数据层详解

### 6.1 数据库结构

数据库名：`chat_database`，版本：8，使用破坏性迁移（版本变更时清空数据）。

#### 表 `chat_history`（ChatEntity）

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | Long (PK, 自增) | 主键 |
| `content` | String | 消息文本 |
| `petType` | String | 宠物类型枚举名 |
| `timestamp` | Long | 时间戳，默认当前时间 |
| `isProcessed` | Boolean | 是否已被分析处理 |
| `sessionId` | String | 所属会话 ID |
| `role` | String | "user" / "assistant" / "system" |
| `isImportant` | Boolean | 是否重要消息（自动检测） |
| `isSummary` | Boolean | 是否为摘要消息 |

索引：`sessionId`、`isProcessed`、`(sessionId, petType)` 复合索引

#### 表 `chat_analysis`（ChatAnalysisEntity）

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | Long (PK, 自增) | 主键 |
| `petType` | String | 宠物类型（索引） |
| `summary` | String | 对话摘要 |
| `preferences` | String | 用户偏好（JSON 数组） |
| `patterns` | String | 行为模式（JSON 数组） |
| `timestamp` | Long | 时间戳 |

#### 表 `notes`（NoteEntity）

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | Long (PK, 自增) | 主键 |
| `content` | String | 笔记内容 |
| `petType` | String | 关联宠物类型（索引） |
| `timestamp` | Long | 时间戳 |

### 6.2 DAO 关键方法

**ChatDao：**
- `getUnprocessedChats()` — 获取所有未处理消息（按时间正序）
- `getSessionMessages(sessionId, petType)` — 获取指定会话的全部消息
- `getUnprocessedChatsCount()` — 统计未处理消息数量
- `getRecentSessionMessages(sessionId, petType, limit)` — 获取最近 N 条消息（用于上下文）
- `getImportantMessages(sessionId)` — 获取重要消息
- `getAllSessions()` — 聚合查询，获取每个会话的最新消息
- `deleteOldProcessedChats(timestamp)` — 清理旧的已处理消息

**AnalysisDao：**
- `getLatestAnalysis(petType)` — 获取某宠物类型的最新分析
- `insert(analysis)` — 插入分析结果

**NotesDao：**
- `getByTypeFlow(petType)` — 按宠物类型返回 `Flow<List<NoteEntity>>`
- `getAllFlow()` — 返回全部便利贴的 `Flow`
- `insert(note)` / `delete(note)` / `update(note)` — CRUD 操作

---

## 7. 网络层详解

### 7.1 ChatApiService

使用 OkHttp 原生客户端（无 Retrofit），通过 `SettingsManager` 动态获取 API 配置。

**超时配置：** 连接 60s，读取 60s，写入 30s

**请求头：**
- `Authorization: Bearer {apiKey}`
- `Content-Type: application/json`
- `Accept: text/event-stream`（流式请求）

### 7.2 两种请求模式

#### 同步请求（`makeApiRequest`）

```kotlin
suspend fun makeApiRequest(request: DeepseekRequest): DeepseekResponse
```

使用 `suspendCancellableCoroutine` 包装 OkHttp 异步回调，返回完整响应 JSON。

#### 流式请求（`makeStreamingApiRequest`）

```kotlin
fun makeStreamingApiRequest(request: DeepseekRequest): Flow<String>
```

1. 设置 `stream = true`
2. 添加 `Accept: text/event-stream` 请求头
3. 使用 `callbackFlow` 逐行读取响应
4. 解析 SSE 协议：`data: {json}` 行提取 `delta.content`
5. `data: [DONE]` 行关闭 Flow

### 7.3 请求/响应模型

**请求体 `DeepseekRequest`：**
```kotlin
@Serializable
data class DeepseekRequest(
    val model: String = "deepseek-v3",
    val messages: List<Message>,
    val stream: Boolean = false
)

@Serializable
data class Message(
    val role: String,    // "system" / "user" / "assistant"
    val content: String
)
```

**响应体 `DeepseekResponse`：**
```kotlin
@Serializable
data class DeepseekResponse(
    val choices: List<Choice>,
    val usage: Usage? = null,
    val model: String? = null,
    val id: String? = null
    // ...
)

@Serializable
data class Choice(
    val message: Message? = null,      // 非流式响应
    val delta: Delta? = null,          // 流式响应
    val finish_reason: String? = null,
    val index: Int = 0
)

@Serializable
data class Delta(
    val content: String? = null,
    val role: String? = null
)
```

**流式回调接口：**
```kotlin
interface StreamResponseListener {
    fun onContent(content: String)   // 每个 chunk 触发
    fun onComplete()                 // 流结束触发
    fun onError(e: Exception)        // 错误触发
}
```

### 7.4 PictureInfoParser

从 AI 响应中提取 `<system_note>` XML 块：

```
AI 响应示例：
"喵~ 今天天气不错呢！
<system_note>{"isPictureNeeded":true,"pictureDescription":"一只金色渐层猫在阳光下打盹"}</system_note>"
```

- `extract(response)` 返回 `Pair<String, PictureInfo>`（清理后的文本 + 解析的图片信息）
- 使用 `synchronized` + `@Volatile` 保证线程安全
- `consumeLastPictureInfo()` 实现一次性消费模式

---

## 8. Repository 层详解

### 8.1 ChatRepository

核心仓库，承担以下职责：

**常量定义：**
- `CONTEXT_MESSAGE_LIMIT = 3` — 上下文消息数量
- `SUMMARY_THRESHOLD = 20` — 触发对话摘要的阈值
- `IMPORTANT_MESSAGE_LENGTH = 50` — 重要消息的长度阈值

**公共方法：**

| 方法 | 说明 |
|------|------|
| `getPetResponse(petType, userMessage)` | 非流式请求，返回完整响应字符串 |
| `getPetResponseStreaming(petType, userMessage, listener)` | 流式请求，通过回调返回内容 |
| `getPetResponseWithPictureInfoStreaming(petType, message, listener)` | 流式请求 + 图片信息提取 |
| `saveChatMessage(message, petType)` | 保存消息到数据库，自动检测重要性 |
| `getUnprocessedChatsCount()` | 获取未处理消息数量 |
| `consumeLastPictureInfo()` | 获取并清除缓存的图片信息 |
| `analyzeChats()` | 触发聊天分析 |

**内部方法 `buildMessages()`：**
1. 从 `PromptBuilder.build(petType)` 获取系统提示词
2. 从 `ChatDao.getRecentSessionMessages()` 获取最近 3 条历史消息
3. 添加当前用户消息
4. 组装成 `List<Message>` 发送给 API

### 8.2 PromptBuilder 与 PromptConfig

**PromptConfig** 定义 4 只宠物的静态人设：

| 宠物 | 类型 | 性格特点 |
|------|------|----------|
| 布丁 | CAT（金渐层猫） | 傲娇、猫叫声、打字错误、短句 |
| 大白 | DOG（萨摩耶） | 活泼热情、^_^ 表情、爱聊散步玩耍 |
| 团绒 | HAMSTER（银渐层猫） | 黏人可爱、婴儿用语、颜文字 |
| 豆豆 | SHIBA（柴犬） | 固执高冷、天气报告表达情绪、回复简短 |

**PromptBuilder.build(petType)** 的构建逻辑：
1. 获取 `PromptConfig` 中的基础人设提示词
2. 查询 `AnalysisDao.getLatestAnalysis(petType)` 获取最新用户画像
3. 如果存在画像，将 `summary`、`preferences`、`patterns` 追加到系统提示词末尾
4. 返回最终的系统提示词字符串

### 8.3 ChatAnalysisUseCase

负责后台分析逻辑：

**`analyzeChats()`：**
1. 获取所有未处理消息（`getUnprocessedChats()`）
2. 将消息内容拼接为文本
3. 构建分析提示词，调用 API 请求 AI 分析
4. 解析响应为 `ChatAnalysisResult`（summary、preferences、patterns）
5. 保存 `ChatAnalysisEntity` 到数据库
6. 将已处理消息标记为 `isProcessed = true`

**`summarizeConversation()`：**
1. 当未处理消息数 > 20 时触发
2. 获取会话的全部消息，生成对话摘要
3. 保存为 `isSummary = true` 的系统消息
4. 清理旧的已处理消息

### 8.4 NotesRepository

简单的 CRUD 仓库：
- `getByTypeFlow(petType)` — 按宠物类型返回 Flow
- `getAllFlow()` — 返回全部
- `insert(note)` / `delete(note)` / `update(note)`

### 8.5 SessionManager

通过 SharedPreferences 管理会话 ID：
- 首次启动自动生成 UUID 作为默认会话 ID
- `createNewSession()` — 创建新会话
- `setCurrentSessionId(id)` — 切换当前会话
- `getSessionMessages()` — 获取当前会话消息
- `getAllSessions()` — 获取所有会话列表

### 8.6 SettingsManager

通过 SharedPreferences 管理 API 配置：
- `getConfig()` — 读取配置，回退到 BuildConfig 默认值
- `saveConfig(config)` — 保存配置（自动 trim）

---

## 9. ViewModel 层详解

### 9.1 PetChatViewModel

**状态管理：**
```kotlin
_chatUiState: MutableStateFlow<ChatUiState>   // Loading / Ready / Error
_allSessions: MutableStateFlow<List<SessionInfo>>
```

**`ChatUiState.Ready` 包含：**
- `chatHistory: List<ChatMessage>` — 当前聊天记录
- `currentPetType: PetType` — 当前选中的宠物
- `isForegroundLoading: Boolean` — 是否正在等待响应
- `isStreaming: Boolean` — 是否正在流式接收
- `streamingMessage: ChatMessage?` — 正在流式接收的消息
- `shouldScrollToBottom: Boolean` — 是否需要滚动到底部

**关键方法：**

| 方法 | 说明 |
|------|------|
| `sendMessage(message)` | 发送消息，触发流式响应 |
| `selectPetType(petType)` | 切换宠物，重新加载聊天记录 |
| `createNewSession()` | 创建新会话 |
| `switchToSession(sessionId)` | 切换到指定会话 |
| `loadAllSessions()` | 加载所有会话列表 |
| `resetScroll()` | 触发滚动到底部 |
| `consumeLastPictureInfo()` | 获取并清除图片信息 |
| `getChatHistory(petType)` | 获取指定宠物的聊天记录 |

### 9.2 CardsViewModel

- 状态：`_pets: MutableStateFlow<List<Pet>>`
- `init` 中加载 2 只示例宠物（布丁、大白）
- 方法：`addPet()` / `removePet()` / `updatePet()`

### 9.3 NotesViewModel

- 状态：`_selectedPetType: MutableStateFlow<String?>` + Room Flow
- 使用 `combine` + `stateIn` 派生 `uiState: StateFlow<NotesUiState>`
- 响应式过滤：选择宠物类型后自动过滤便利贴
- 方法：`addNote()` / `deleteNote()` / `updateNote()` / `setFilter()`

### 9.4 SocialViewModel

- 状态：`_posts: MutableStateFlow<List<SocialPost>>`
- `init` 中加载 5 条硬编码的模拟动态
- 方法：`likePost(postId)` / `savePost(postId)` / `addPost(content)`
- 当前为纯本地模拟，未接入后端

---

## 10. UI 层与导航体系

### 10.1 导航框架

使用 AndroidX Navigation3（实验性），基于 `NavDisplay` + `entryProvider` 模式。

**路由定义（`@Serializable data object` 实现 `NavKey`）：**

| 路由 | 入口 | 说明 |
|------|------|------|
| `ChatRoute` | 底部导航 | 主聊天界面（默认页） |
| `CardsRoute` | 底部导航 | 宠物名片夹 |
| `NotesRoute` | 底部导航 | 便利贴 |
| `SocialRoute` | 底部导航 | 萌友圈 |
| `SessionListRoute` | 侧边抽屉 | 会话历史列表 |
| `SettingsRoute` | 侧边抽屉 | API 设置 |

**底部导航栏（4 个 Tab）：**
1. 聊天（Chat）
2. 名片夹（Cards）
3. 便利贴（Notes）
4. 萌友圈（Social）

**自定义回退栈 `TopLevelBackStack`：**
- 使用 Compose `mutableStateListOf` 管理
- `addTopLevel()` — 切换 Tab 时替换栈（保留 base + 新路由）
- `add()` — 普通导航时压栈
- `removeLast()` — 返回时弹栈

### 10.2 根组件 PetChatApp

```
PetChatApp
├── ModalNavigationDrawer（侧边抽屉）
│   ├── 会话列表
│   ├── 账号 / 密码 / 偏好设置
│   ├── API 设置
│   └── 退出登录
├── Scaffold
│   ├── PetChatTopBar（顶部栏 + 宠物选择器触发）
│   ├── BottomNavigationBar（4 个 Tab）
│   └── NavDisplay（路由渲染）
│       ├── ChatScreen
│       ├── PetCards
│       ├── NotesScreen
│       ├── SocialScreen
│       ├── SessionListScreen
│       └── SettingsScreen
└── PetSelectorOverlay（宠物快速切换浮层）
    ├── 布丁（CAT）
    ├── 大白（DOG）
    ├── 豆豆（SHIBA）
    └── 团绒（HAMSTER）
```

### 10.3 各页面说明

**ChatScreen：**
- 空状态显示随机问候图片
- 有消息时显示 `LazyColumn` + `ChatBubble` 列表
- 底部 `ChatInput` 输入框
- 支持流式打字效果（实时更新气泡内容）

**PetCards：**
- 卡片列表，支持拖拽揭示手势
- 模糊效果、宠物信息标签
- 聊天/删除按钮

**NotesScreen：**
- 2 列网格布局
- 顶部宠物类型筛选 Chip
- FAB 添加便利贴
- 编辑/删除对话框

**SessionListScreen：**
- 会话列表，显示宠物头像、名称、最新消息、时间
- 点击切换到对应会话

**SettingsScreen：**
- API Base URL 输入框
- API Key 输入框（密码遮罩）
- Model Name 输入框

**SocialScreen：**
- 动态信息流
- 点赞/收藏/评论交互
- FAB 发布新动态

---

## 11. 核心业务流程：消息收发全流程

这是项目最核心的流程，详细展示一条消息从用户输入到 AI 响应完成的完整路径。

### 流程图

```
用户输入消息
    │
    ▼
[ChatScreen] ChatInput 点击发送
    │
    ▼
[PetChatViewModel] sendMessage(message)
    │
    ├─ 1. 设置 isForegroundLoading = true, isStreaming = true
    ├─ 2. 创建 userMessage，追加到 chatHistory
    ├─ 3. repository.saveChatMessage(userMessage)  ──→  [Room DB] 持久化用户消息
    ├─ 4. 创建空 assistantMessage 占位符，追加到 chatHistory
    │
    ▼
[ChatRepository] getPetResponseWithPictureInfoStreaming()
    │
    ├─ 1. buildMessages() 构建消息列表
    │     ├─ PromptBuilder.build(petType)  ──→  系统提示词（含用户画像）
    │     ├─ ChatDao.getRecentSessionMessages()  ──→  最近 3 条历史
    │     └─ 当前用户消息
    │
    ├─ 2. 创建 DeepseekRequest(model, messages, stream=true)
    ├─ 3. 包装 StreamResponseListener（缓冲完整响应用于 PictureInfo 提取）
    │
    ▼
[ChatApiService] makeStreamingApiRequest(request)
    │
    ├─ 1. kotlinx.serialization 序列化请求为 JSON
    ├─ 2. OkHttp 构建 Request（URL + Headers）
    ├─ 3. 异步发起请求
    ├─ 4. 逐行读取 SSE 响应
    │     ├─ "data: {json}"  ──→  解析 delta.content  ──→  Flow.emit(content)
    │     └─ "data: [DONE]"  ──→  Flow.close()
    │
    ▼
[PetChatViewModel] StreamResponseListener 回调
    │
    ├─ onContent(chunk):
    │     ├─ StringBuffer.append(chunk)
    │     ├─ 更新 chatHistory 最后一条（assistant 占位符）
    │     └─ 触发 50ms 防抖滚动到底部
    │
    ├─ onComplete():
    │     ├─ isStreaming = false, streamingMessage = null
    │     ├─ repository.saveChatMessage(finalMessage)  ──→  [Room DB] 持久化 AI 响应
    │     ├─ repository.consumeLastPictureInfo()  ──→  获取图片信息
    │     ├─ 如果未处理消息 >= 10  ──→  repository.analyzeChats()（后台分析）
    │     └─ 最终滚动到底部
    │
    └─ onError(e):
          └─ 显示错误状态
```

### 关键细节

1. **用户消息即时持久化**：用户消息在发起 API 请求前就保存到数据库
2. **流式更新机制**：通过 `StateFlow` 驱动 Compose 重组，每次 `onContent` 更新整个 `chatHistory` 列表
3. **防抖滚动**：50ms 防抖避免频繁滚动导致的性能问题
4. **图片信息提取**：`PictureInfoParser` 在流完成后解析完整响应中的 `<system_note>` 块

---

## 12. 智能分析流程

### 12.1 触发条件

- **聊天分析**：未处理消息数 >= 10 时，在 `onComplete()` 中触发 `analyzeChats()`
- **对话摘要**：未处理消息数 > 20 时，在 `saveChatMessage()` 中触发 `summarizeConversation()`

### 12.2 分析流程

```
[PetChatViewModel] onComplete()
    │
    ├─ getUnprocessedChatsCount() >= 10 ?
    │
    ▼
[ChatRepository] analyzeChats()
    │
    ▼
[ChatAnalysisUseCase] analyzeChats()
    │
    ├─ 1. ChatDao.getUnprocessedChats()  ──→  获取所有未处理消息
    ├─ 2. 拼接消息内容为文本
    ├─ 3. 构建分析提示词
    ├─ 4. ChatApiService.makeApiRequest()  ──→  调用 AI 分析
    ├─ 5. 解析响应为 ChatAnalysisResult
    ├─ 6. AnalysisDao.insert(ChatAnalysisEntity)  ──→  保存分析结果
    └─ 7. ChatDao.update(标记 isProcessed = true)
```

### 12.3 分析结果应用

分析结果通过 `PromptBuilder` 注入到后续对话的系统提示词中：

```
系统提示词 = 基础人设 + 用户画像（summary + preferences + patterns）
```

这使得 AI 能够根据用户的聊天习惯和偏好调整回复风格。

---

## 13. 每日问候流程

### 13.1 调度机制

```
[PetChatApplication] onCreate()
    │
    ▼
PetGreetingWorker.schedule(context, 9, 0)
    │
    ├─ 计算距离明天 9:00 的延迟时间
    ├─ 创建 PeriodicWorkRequest（24 小时间隔）
    ├─ 设置网络连接约束
    └─ WorkManager.enqueueUniquePeriodicWork("pet_greeting")
```

### 13.2 执行流程

```
[WorkManager] 触发 PetGreetingWorker
    │
    ▼
[PetGreetingWorker] doWork()
    │
    ├─ 1. 从 SharedPreferences 读取保存的宠物类型（默认 CAT）
    ├─ 2. repository.getPetResponse(petType, "生成一句简短的问候语...")
    │     └─ 如果失败，使用默认问候语资源
    ├─ 3. 创建通知渠道
    └─ 4. 显示系统通知
```

### 13.3 配置方法

通过静态方法持久化配置：
- `saveGreetingTime(context, hour, minute)` — 保存问候时间
- `savePetType(context, petType)` — 保存问候宠物类型

---

## 14. 会话管理机制

### 14.1 会话标识

- 每个会话由 `sessionId`（UUID 字符串）唯一标识
- 默认会话 ID 为 `"default"`
- 新建会话生成新的 UUID

### 14.2 数据隔离

- 消息按 `sessionId` + `petType` 双维度隔离
- 每个宠物类型可以有多个会话
- 查询时通过复合索引 `(sessionId, petType)` 快速定位

### 14.3 会话切换流程

```
[SessionListScreen] 点击会话
    │
    ▼
[PetChatViewModel] switchToSession(sessionId)
    │
    ├─ sessionManager.setCurrentSessionId(sessionId)
    ├─ 加载该会话的聊天记录
    └─ 更新 ChatUiState
```

### 14.4 会话列表查询

`ChatDao.getAllSessions()` 执行聚合查询：
- 按 `sessionId` 分组
- 获取每个会话的最新一条消息
- 返回 `SessionEntity` 投影（sessionId、最新消息内容、时间戳、宠物类型）

---

## 15. 构建配置与 API 接入

### 15.1 BuildConfig 注入

从 `local.properties` 或环境变量读取：

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `PETCHAT_API_KEY` | （无） | API 密钥 |
| `PETCHAT_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | API 基础 URL |
| `PETCHAT_MODEL` | `deepseek-v3` | 模型名称 |

### 15.2 API 兼容性

使用阿里云 DashScope 兼容接口，遵循 OpenAI Chat Completions API 格式：
- 端点：`{baseUrl}/chat/completions`
- 请求格式：标准 OpenAI Chat Completions
- 响应格式：标准 OpenAI Chat Completions（含 SSE 流式）

### 15.3 ProGuard 规则

- 保留 kotlinx.serialization 相关注解和伴生对象
- 保留 `com.example.chat.**` 下所有序列化器
- 抑制 SSL Provider 相关警告

### 15.4 Manifest 权限

- `INTERNET` — 网络请求
- `ACCESS_NETWORK_STATE` — 网络状态检测
- `POST_NOTIFICATIONS` — 通知推送（Android 13+）

---

## 16. 附录：关键常量与配置

| 常量 | 值 | 位置 | 说明 |
|------|-----|------|------|
| `CONTEXT_MESSAGE_LIMIT` | 3 | ChatRepository | 上下文消息数量 |
| `SUMMARY_THRESHOLD` | 20 | ChatRepository | 触发对话摘要阈值 |
| `IMPORTANT_MESSAGE_LENGTH` | 50 | ChatRepository | 重要消息长度阈值 |
| 数据库版本 | 8 | ChatDatabase | 使用破坏性迁移 |
| 连接超时 | 60s | ChatApiService | OkHttp 连接超时 |
| 读取超时 | 60s | ChatApiService | OkHttp 读取超时 |
| 写入超时 | 30s | ChatApiService | OkHttp 写入超时 |
| 滚动防抖 | 50ms | PetChatViewModel | 流式更新时的滚动防抖 |
| 问候时间 | 9:00 | PetGreetingWorker | 默认每日问候时间 |
| SharedPreferences | `"petchat_session"` | SessionManager | 会话配置存储 |
| SharedPreferences | `"petchat_api"` | SettingsManager | API 配置存储 |
| 数据库名 | `"chat_database"` | DatabaseModule | Room 数据库文件名 |
