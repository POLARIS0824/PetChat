# AI Pet Chat - 智能宠物聊天应用

一个基于 Android 的智能宠物聊天应用，使用 AI 模型实现个性化的宠物对话体验。

## 功能特点

### 1. 智能宠物聊天
- 支持多种宠物角色（猫咪、狗狗）
- AI 根据宠物类型调整性格和对话风格
- 个性化的对话内容和表达方式
- 支持表情和拟声词的自然表达

### 2. 用户画像分析
- 自动分析用户聊天记录（每10条对话）
- 提取用户偏好和兴趣点
- 记录互动模式和行为特征
- AI 根据分析结果动态调整回复风格

### 3. 定时问候功能
- 每日定时发送个性化问候
- 可自定义问候时间
- 系统通知推送
- 智能生成符合宠物性格的问候语

## 技术架构

### 架构模式
- MVVM (Model-View-ViewModel) 架构
- Repository 模式管理数据
- 单例模式确保组件单一实例

### 核心组件

#### 1. UI 层
- `MainActivity`: 应用主界面
- `PetChatApp`: Compose UI 主组件
- `ChatBubble`: 聊天消息气泡组件

#### 2. ViewModel 层
- `PetChatViewModel`: 管理 UI 状态和用户交互

#### 3. Repository 层
- `PetChatRepository`: 处理数据和 AI 交互
- 管理本地数据库和网络请求

#### 4. 数据层
- Room 数据库存储聊天记录和分析结果
- 数据实体：
  - `ChatEntity`: 聊天消息实体
  - `ChatAnalysisEntity`: 用户分析结果实体

#### 5. 服务组件
- `PetGreetingWorker`: 处理定时问候服务

### 使用的技术

- **UI**: Jetpack Compose
- **数据库**: Room
- **后台任务**: WorkManager
- **网络**: OkHttp
- **JSON处理**: Gson
- **异步处理**: Kotlin Coroutines
- **AI集成**: DeepSeek API

## 数据流程

### 聊天流程
1. 用户输入消息
2. ViewModel 处理输入
3. Repository 调用 AI API
4. 更新 UI 显示回复
5. 保存聊天记录

### 分析流程
1. 累积 10 条未处理消息
2. 提取聊天记录
3. AI 分析生成用户画像
4. 保存分析结果
5. 用于优化后续对话

### 问候流程
1. WorkManager 定时触发
2. 获取用户画像
3. 生成个性化问候
4. 发送系统通知

## 项目结构

</rewritten_file> 