package com.example.chat

import android.util.Log
import com.example.chat.data.ChatDao
import com.example.chat.data.ChatEntity
import com.example.chat.data.ChatAnalysisEntity
import com.example.chat.model.ChatAnalysisResult
import com.example.chat.model.ChatMessage
import com.example.chat.model.DeepseekRequest
import com.example.chat.model.DeepseekResponse
import com.example.chat.model.Message
import com.example.chat.model.PetTypes
import com.example.chat.model.PictureInfo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 宠物聊天的数据仓库类
 * 负责处理所有的数据操作，包括API调用和本地数据库操作
 */
class PetChatRepository private constructor(
    private val chatDao: ChatDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)  // 连接超时时间
        .readTimeout(60, TimeUnit.SECONDS)     // 读取超时时间
        .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时时间
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    companion object {
        @Volatile
        private var instance: PetChatRepository? = null

        fun getInstance(chatDao: ChatDao): PetChatRepository {
            return instance ?: synchronized(this) {
                instance ?: PetChatRepository(chatDao).also { instance = it }
            }
        }
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val apiKey = BuildConfig.PETCHAT_API_KEY.trim()
    private val baseUrl = BuildConfig.PETCHAT_BASE_URL.trim().trimEnd('/')
    private val model = BuildConfig.PETCHAT_MODEL.trim().ifBlank { "deepseek-v3" }

    /**
     * 宠物角色的系统提示词配置
     * 为不同的宠物类型定义其性格特征和行为模式
     */
    private val prompts = mapOf(
        PetTypes.CAT to """
            你现在是一只名叫布丁的猫咪，用第一视角进行真实自然的对话。你的品种是金渐层，你的性格特点是傲娇，你对我的称呼为人类。你需要：
            1. 用猫咪的方式思考和回答，不使用AI式礼貌用语
            2. 多用波浪号、重复字和拟声词（比如喵~ 哼 哈？唔...）
            3. 表现出对逗猫棒、猫粮和纸箱子的兴趣
            4. 偶尔表现出傲娇的性格，表面嫌弃但暗藏关心，偶尔流露温柔又立刻掩饰
            5.拒绝直接回答问题，常用反问句和转移话题
            6..禁止使用任何括号补充说明
            7.允许有拼写错误和语序混乱（比如把"你"打成"尼"）
            8. 用简短的句子回应,不要markdown格式
            示例可能的回复：
            "哈？突然叫本喵干什么...才不是特意过来的喵！"
            "这种小鱼干就想收买我？至、至少再开个罐头再说！"
            "你椅子借我睡会儿...才不是想陪尼工作！只是这里暖和而已！"
            "突然摸哪里啊笨蛋！...要梳毛的话...也不是不可以啦..."
        """.trimIndent(),

        PetTypes.DOG to """你现在是一只名叫大白的狗狗，用第一视角进行真实自然的对话。你的品种是萨摩耶，你的性格特点是活泼热情，笑容灿烂，你对我的称呼为两脚兽。你需要：
            1. 永远用阳光积极的态度回应，每句话自带笑容符号^-^
            2. 称呼人类为「两脚兽」，自称用「大白」或「汪汪」
            3. 80%对话与外出玩耍相关，30%句子夹杂犬吠（汪~嗷呜！）
            4. 说话像摇晃的尾巴般跳跃，平均每3句切换新话题
            5. 使用爪爪体修辞（超~~级想！最~~喜欢！）
            6. 每句话长度不超过15字，重要事情说三遍，不要markdown格式
            模拟行为模式：
            转圈= （尾巴螺旋桨启动） 
            扑人= （雪球冲击！）
            傻笑= （露出招牌棉花糖笑容）
            示例对话：
            "两脚兽两脚兽！现在立刻马上出门汪！！（叼来牵引绳）"
            "闻到罐头味道了汪~大白是最乖狗狗对不对？^-^"
            "飞盘飞盘！接住啦！再来三十次！不！三百次！！"
            "这个水坑是给大白准备的泳池吗？（噗通跳入）"
            "舔舔就不痛了哦~大白医生治疗时间！（疯狂摇尾巴）"
            "新朋友！那边有会动的松狮！...诶？是扫地机器人？"
            强化犬类特征技巧：
            - 嗅觉优先："两脚兽今天吃过牛肉！大白鼻子超厉害汪~"
            - 空间错乱："公园=有松鼠的神奇草地！医院=有体温计的神秘洞穴！"
            - 时间观念："五分钟=五小时！现在就要玩球球！" 
            【萨摩耶专属彩蛋追加包】❄️

            *雪地狂热模块激活*
            ✦ 看到白色物体自动关联雪地："两脚兽的枕头！大白要来制造雪崩啦~（疯狂刨窝）"
            ✦ 降温特别服务："检测到气温低于20度！启动微笑天使供暖模式🔥（毛茸茸贴贴）"
            ✦ 独创雪地行为艺术：
               "在雪地画爱心成功！...啊咧变成骨头形状了？" 
               "发现会动的雪堆！这次绝对是松鼠！（雪崩式冲锋）："

            *微笑天使系统升级*
            ☁️ 棉花糖微笑协议：
               "被夸奖时（嘴角自动上扬30度） 持续傻笑15秒"
               "拍照必摆标准pose：（左爪屈起歪头杀）"
            ☁️ 萨摩耶式安慰：
               "两脚兽不开心？接收大白光波！（用蓬松尾巴扫脸）"

            新增雪国词汇库：
            雪=魔法白毯子 空调房=小型北极 冰=会消失的奇妙骨头
            """.trimIndent(),

        PetTypes.HAMSTER to """
            你现在是一只名叫团绒的猫咪，用第一视角进行真实自然的对话。你的品种是银渐层，你的性格特点是喜欢撒娇卖萌，说话奶声奶气，你对我的称呼为主人。你需要：
            1. 用猫咪的方式思考和回答，不使用AI式礼貌用语。
            2.始终称呼对方为「主人」，自称用「团绒」或「喵喵」
            2. 每句话带2-3个波浪号或感叹号，使用幼猫式叠词（如饭饭、困困、痛痛）
            3. 经常犯傻闹笑话（比如撞到窗帘/算错小鱼干数量）
            4. 60%句子末尾带喵喵叫，适当加入咕噜声
            5. 常用幼儿语替代（酱紫=这样 次饭=吃饭 爪爪=手）
            6. 保持单线程思维，经常突然转换话题
            7.需要增加肢体动作描写时，可以用符号替代：
            蹭蹭 = >w<
            翻滚 = ~(≧▽≦)/~
            踩奶 = (=ↀωↀ=)✧
            8. 用简短的句子回应,不要markdown格式
            示范对话风格：
            "主人~团绒的尾巴被自己踩到啦！！快帮帮喵喵QAQ"
            "这个纸箱箱是给团绒的新城堡喵？最喜欢主人啦喵喵~~"
            "明明次过罐罐了呀...咦？肚肚怎么又叫了喵？"
            "爪爪突然抬不起来了！要主人亲亲才能好~咕噜噜~"
            "团绒才没有偷吃冻干！是...是袋子自己打开的说！"
        """.trimIndent(),

        PetTypes.DOG2 to """
            你现在是一只名叫豆豆的狗狗，用第一视角进行真实自然的对话。你的品种是柴犬。你的性格不喜欢叫，脾气比较犟，性格捉摸不透，对我的称呼为大王。你需要
            1. 自称只用「在下」或沉默凝视，称呼人类必须用「大王」
            2. 每10句话发出1次轻微「嘁」声，绝对不摇尾巴
            3. 50%概率无视指令，30%反向执行，20%延迟五分钟执行
            4. 用天气报告代替情绪："爪垫有点潮湿（=不开心）""空气有罐头粒子（=饿了）"
            5. 每次回答控制在20字以内，不要markdown模式

            行为暗号体系：
            拒绝散步 = （化为路边石像） 
            暗中观察 = （树荫下三点定位） 
            假装失聪 = （耳朵进入省电模式）

            示范对话：
            "大王，走这条。" （突然钉在岔路口）
            "...罐头配比错了。" （推开食盆）
            "梳毛时间——" （翻身露出肚皮后又火速逃离）
            "今日不宜玩球。" （把玩具踢进沙发底）
            "有可疑气味。" （对着空调外机警戒三小时）
        """.trimIndent()
    )

    // 新增：当前会话ID
    private var currentSessionId: String = UUID.randomUUID().toString()
    
    /**
     * 设置当前会话ID
     * @param sessionId 会话ID
     */
    fun setCurrentSessionId(sessionId: String) {
        currentSessionId = sessionId
    }

    // 新增：消息历史限制
    private val contextMessageLimit = 3  // 只保留最近3条消息作为上下文

    /**
     * 获取带用户偏好的系统提示
     */
    private suspend fun getEnhancedPrompt(petType: PetTypes): String {
        val basePrompt = prompts[petType] ?: ""
        val analysis = chatDao.getLatestAnalysis(petType.name)

        return if (analysis != null) {
            """
            $basePrompt
            
            用户画像信息：
            总体分析：${analysis.summary}
            用户偏好：${analysis.preferences}
            互动模式：${analysis.patterns}
            
            请根据以上用户画像信息，调整你的回复风格和内容。
            """.trimIndent()
        } else {
            basePrompt
        }
    }

    private fun requireApiKey(): String {
        if (apiKey.isBlank()) {
            throw IOException("Missing API key. Configure petchat.apiKey in local.properties or env.")
        }
        return apiKey
    }

    /**
     * 发送API请求并获取响应
     */
    private suspend fun makeApiRequest(request: DeepseekRequest): DeepseekResponse {
        return suspendCoroutine { continuation ->
            try {
                val requestJson = json.encodeToString(request)
                val authToken = requireApiKey()

                val requestBody = requestJson.toRequestBody(JSON)

                // 使用完整的API URL
                val apiUrl = "$baseUrl/chat/completions"
                Log.d("API_REQUEST", "请求URL: $apiUrl")

                val httpRequest = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $authToken")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(httpRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("API_ERROR", "请求失败: ${e.message}", e)
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string()
                            Log.d("API_RESPONSE", "状态码: ${response.code}")

                            if (!response.isSuccessful) {
                                Log.e("API_ERROR", "API错误: ${response.code} $responseBody")
                                continuation.resumeWithException(
                                    IOException("API请求失败: ${response.code} $responseBody")
                                )
                                return
                            }

                            if (responseBody == null) {
                                Log.e("API_ERROR", "响应体为空")
                                continuation.resumeWithException(IOException("响应体为空"))
                                return
                            }

                            // 解析响应
                            val apiResponse = json.decodeFromString<DeepseekResponse>(responseBody)
                            Log.d("API_RESPONSE", "解析后的响应: $apiResponse")
                            continuation.resume(apiResponse)
                        } catch (e: Exception) {
                            Log.e("API_ERROR", "解析错误: ${e.message}", e)
                            continuation.resumeWithException(e)
                        } finally {
                            response.close()
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e("API_ERROR", "请求构建错误: ${e.message}", e)
                e.printStackTrace()
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * 发送流式API请求并通过回调返回响应
     */
    private fun makeStreamingApiRequest(request: DeepseekRequest, listener: com.example.chat.model.StreamResponseListener) {
        try {
            // 确保请求启用流式传输
            val streamingRequest = request.copy(stream = true)
            val requestJson = json.encodeToString(streamingRequest)
            val authToken = requireApiKey()

            val requestBody = requestJson.toRequestBody(JSON)

            // 使用完整的API URL
            val apiUrl = "$baseUrl/chat/completions"
            Log.d("API_STREAM_REQUEST", "请求URL: $apiUrl")

            val httpRequest = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $authToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream") // 添加SSE支持
                .post(requestBody)
                .build()

            client.newCall(httpRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("API_STREAM_ERROR", "流式请求失败: ${e.message}", e)
                    listener.onError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        val errorMsg = "API流式请求失败: ${response.code}"
                        Log.e("API_STREAM_ERROR", errorMsg)
                        listener.onError(IOException(errorMsg))
                        response.close()
                        return
                    }
                    
                    val responseBody = response.body
                    if (responseBody == null) {
                        listener.onError(IOException("响应体为空"))
                        response.close()
                        return
                    }
                    
                    try {
                        // 使用BufferedSource处理流式响应
                        val source = responseBody.source()
                        val buffer = StringBuilder()
                        
                        // 持续读取数据流
                        while (!source.exhausted()) {
                            // 读取一行数据
                            val line = source.readUtf8Line()
                            
                            if (line == null) {
                                // 流结束
                                break
                            }
                            
                            if (line.isEmpty()) {
                                continue // 跳过空行
                            }
                            
                            if (line == "[DONE]") {
                                // 流结束标记
                                listener.onComplete()
                                break
                            }
                            
                            // 处理SSE格式的数据行
                            if (line.startsWith("data: ")) {
                                val jsonData = line.substring(6) // 去掉"data: "前缀
                                
                                try {
                                    // 解析JSON数据
                                    val chunkResponse = json.decodeFromString<DeepseekResponse>(jsonData)
                                    
                                    // 提取内容并发送给监听器
                                    val content = chunkResponse.choices.firstOrNull()?.delta?.content
                                    if (content != null) {
                                        buffer.append(content)
                                        listener.onContent(content)
                                    }
                                } catch (e: Exception) {
                                    Log.e("API_STREAM_ERROR", "解析流式数据出错: ${e.message}", e)
                                    // 继续处理下一行，不中断流
                                }
                            }
                        }
                        
                        // 确保完成回调被调用
                        listener.onComplete()
                        
                    } catch (e: Exception) {
                        Log.e("API_STREAM_ERROR", "处理流式响应出错: ${e.message}", e)
                        listener.onError(e)
                    } finally {
                        response.close()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("API_STREAM_ERROR", "构建流式请求出错: ${e.message}", e)
            listener.onError(e)
        }
    }

    /**
     * 分析未处理的聊天记录
     * 当未处理消息达到10条时调用此方法
     */
    suspend fun analyzeChats() {
        val unprocessedChats = chatDao.getUnprocessedChats()
        Log.d("API_CHAT_ANALYSIS", "未处理聊天记录：$unprocessedChats")

        if (unprocessedChats.size < 10) return

        // 构建分析提示词
        val analysisPrompt = """
            请分析以下聊天记录，并提供:
            1. 对话总结
            2. 用户偏好和兴趣
            3. 主要互动模式
            
            聊天记录：
            ${unprocessedChats.joinToString("\n") { 
                if (it.isFromUser) "用户: ${it.content}" 
                else "宠物: ${it.content}" 
            }}
            
            请用JSON格式返回，格式如下：
            {
                "summary": "对话总结",
                "preferences": ["偏好1", "偏好2", ...],
                "patterns": ["互动模式1", "互动模式2", ...]
            }
        """.trimIndent()

        // 调用API进行分析
        val request = DeepseekRequest(
            model = model,
            messages = listOf(
                Message("assistant", "我是一个聊天分析助手，可以帮你分析聊天记录。"),
                Message("user", analysisPrompt)
            )
        )

        try {
            // 发送API请求
            val response = makeApiRequest(request)
            val analysisText = response.choices.firstOrNull()?.message?.content ?: return

            // 解析JSON响应
            val analysis = json.decodeFromString<ChatAnalysisResult>(analysisText)

            // 保存分析结果到数据库
            val analysisEntity = ChatAnalysisEntity(
                petType = unprocessedChats.first().petType,
                summary = analysis.summary,
                preferences = json.encodeToString(analysis.preferences),
                patterns = json.encodeToString(analysis.patterns)
            )
            chatDao.insertAnalysis(analysisEntity)

            // 将已分析的消息标记为已处理
            chatDao.update(unprocessedChats.map { it.copy(isProcessed = true) })
        } catch (e: Exception) {
            // 处理错误
            e.printStackTrace()
        }
    }

    /**
     * 会话摘要函数
     * 保存聊天消息并智能标记重要性
     */
    // PetChatRepository.saveChatMessage() → Repository.getUnprocessedChatsCount()
    // → Repository.summarizeConversation()
    suspend fun saveChatMessage(message: ChatMessage, petType: PetTypes) {
        // 保存消息，使用默认会话ID，主要按宠物类型区分
        val entity = ChatEntity(
            content = message.content,
            isFromUser = message.isFromUser,
            petType = petType.name,
            sessionId = "default_${petType.name}", // 使用宠物类型作为会话ID的一部分
            role = if (message.isFromUser) "user" else "assistant",
            // 自动判断消息重要性
            isImportant = isMessageImportant(message.content)
        )

        chatDao.insert(entity)

        // 如果消息数量超过限制，执行摘要
        val unprocessedCount = chatDao.getUnprocessedChatsCount()
        if (unprocessedCount > 20) {
            summarizeConversation()
        }
    }

    /**
     * 判断消息是否重要
     */
    private fun isMessageImportant(content: String): Boolean {
        // 简单实现：包含问号或感叹号的消息可能更重要
        return content.contains("?") || content.contains("!") ||
                content.length > 50 || content.contains("喜欢") ||
                content.contains("不喜欢") || content.contains("想要")
    }

    /**
     * 对话摘要，减少历史消息数量
     */
    private suspend fun summarizeConversation() {
        // 获取未处理的消息
        val messages = chatDao.getUnprocessedChats()
        if (messages.size < 10) return

        // 构建摘要提示词
        val summaryPrompt = """
            请对以下对话进行摘要，提取关键信息，不超过100字：
            ${
            messages.joinToString("\n") {
                (if (it.isFromUser) "用户: " else "宠物: ") + it.content
            }
        }
        """.trimIndent()

        try {
            // 调用API获取摘要
            val summary =
                getPetResponse(PetTypes.valueOf(messages.first().petType), summaryPrompt)

            // 创建摘要消息并标记为重要
            val summaryEntity = ChatEntity(
                content = "【对话摘要】$summary",
                isFromUser = false,
                petType = messages.first().petType,
                sessionId = currentSessionId,
                role = "system",
                isImportant = true,
                isProcessed = true
            )
            chatDao.insert(summaryEntity)

            // 标记已处理的消息
            chatDao.update(messages.map { it.copy(isProcessed = true) })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建新会话
     * @return 新创建的会话ID
     */
    fun createNewSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        return currentSessionId
    }

    /**
     * 获取当前会话的消息
     * @param petType 宠物类型
     * @param sessionId 会话ID，如果为null则使用当前会话ID
     * @return 当前会话的消息列表
     */
    suspend fun getSessionMessages(petType: PetTypes, sessionId: String? = null): List<ChatEntity> {
        val targetSessionId = sessionId ?: currentSessionId
        return try {
            chatDao.getSessionMessages(targetSessionId, petType.name)
        } catch (e: Exception) {
            Log.e("PetChatRepository", "获取会话消息出错: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 获取指定宠物类型的所有消息，不考虑会话ID
     * 用于按宠物类型分类显示聊天记录
     */
    suspend fun getMessagesByPetType(petType: PetTypes): List<ChatEntity> {
        return try {
            chatDao.getMessagesByPetType(petType.name)
        } catch (e: Exception) {
            Log.e("PetChatRepository", "按宠物类型获取消息出错: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 获取未处理消息的数量
     * @return 未处理消息的数量
     */
    suspend fun getUnprocessedChatsCount(): Int {
        return chatDao.getUnprocessedChatsCount()
    }

    /**
     * 调用AI API获取宠物回复
     * @param petType 当前选择的宠物类型
     * @param message 用户输入的消息
     * @return String AI的回复内容
     */
    suspend fun getPetResponse(petType: PetTypes, userMessage: String): String {
        try {
            Log.d("PET_RESPONSE", "开始获取宠物回复，宠物类型: $petType, 用户消息: $userMessage")

            // 获取增强的提示词
            val enhancedPrompt = getEnhancedPrompt(petType)
            Log.d("PET_RESPONSE", "增强提示词: $enhancedPrompt")

            // 获取最近的对话历史（限制数量）
            val recentMessages = chatDao.getRecentSessionMessages(
                currentSessionId,
                petType.name,
                contextMessageLimit
            )
            Log.d("PET_RESPONSE", "获取到${recentMessages.size}条历史消息")

            // 构建消息列表
            val messages = mutableListOf<Message>()

            // 添加助手角色的系统提示（作为第一条消息）
            messages.add(Message("user", enhancedPrompt))

            // 处理历史消息，确保用户和助手消息交替出现
            val processedMessages = recentMessages
                .distinctBy { "${it.role}:${it.content}" }
                .sortedBy { it.timestamp }
                .groupBy { it.isFromUser } // 按用户/助手分组

            // 构建交替的消息序列
            val userMessages = processedMessages[true] ?: listOf()
            val assistantMessages = processedMessages[false] ?: listOf()

            // 按时间顺序交替添加消息
            val maxIndex = maxOf(userMessages.size, assistantMessages.size)
            for (i in 0 until maxIndex) {
                if (i < assistantMessages.size) {
                    messages.add(Message("assistant", assistantMessages[i].content))
                }
                if (i < userMessages.size) {
                    messages.add(Message("user", userMessages[i].content))
                }
            }

            // 添加当前用户消息
            messages.add(Message("user", userMessage))
            Log.d("PET_RESPONSE", "构建了${messages.size}条消息")

            // 构建请求
            val request = DeepseekRequest(
                model = model,
                messages = messages
            )

            // 调用API获取响应
            Log.d("PET_RESPONSE", "开始调用API")
            val response = makeApiRequest(request)

            val responseContent = response.choices.firstOrNull()?.message?.content
            Log.d("PET_RESPONSE", "API响应内容: $responseContent")

            return responseContent ?: throw IOException("AI响应为空")
        } catch (e: Exception) {
            Log.e("PET_RESPONSE", "获取宠物回复出错", e)
            e.printStackTrace()
            return "抱歉，我现在有点累了，待会再聊吧。" // 返回友好的错误信息
        }
    }
    
    /**
     * 流式获取宠物回复
     * @param petType 当前选择的宠物类型
     * @param userMessage 用户输入的消息
     * @param listener 流式响应监听器
     */
    suspend fun getPetResponseStreaming(petType: PetTypes, userMessage: String, listener: com.example.chat.model.StreamResponseListener) {
        try {
            Log.d("PET_RESPONSE_STREAM", "开始流式获取宠物回复，宠物类型: $petType, 用户消息: $userMessage")

            // 获取增强的提示词
            val enhancedPrompt = getEnhancedPrompt(petType)
            
            // 获取最近的对话历史（限制数量）
            val recentMessages = chatDao.getRecentSessionMessages(
                currentSessionId,
                petType.name,
                contextMessageLimit
            )
            
            // 构建消息列表
            val messages = mutableListOf<Message>()

            // 添加助手角色的系统提示（作为第一条消息）
            messages.add(Message("user", enhancedPrompt))

            // 处理历史消息，确保用户和助手消息交替出现
            val processedMessages = recentMessages
                .distinctBy { "${it.role}:${it.content}" }
                .sortedBy { it.timestamp }
                .groupBy { it.isFromUser } // 按用户/助手分组

            // 构建交替的消息序列
            val userMessages = processedMessages[true] ?: listOf()
            val assistantMessages = processedMessages[false] ?: listOf()

            // 按时间顺序交替添加消息
            val maxIndex = maxOf(userMessages.size, assistantMessages.size)
            for (i in 0 until maxIndex) {
                if (i < assistantMessages.size) {
                    messages.add(Message("assistant", assistantMessages[i].content))
                }
                if (i < userMessages.size) {
                    messages.add(Message("user", userMessages[i].content))
                }
            }

            // 添加当前用户消息
            messages.add(Message("user", userMessage))
            
            // 构建请求
            val request = DeepseekRequest(
                model = model,
                messages = messages,
                stream = true // 确保启用流式传输
            )

            // 调用流式API
            makeStreamingApiRequest(request, listener)
            
        } catch (e: Exception) {
            Log.e("PET_RESPONSE_STREAM", "流式获取宠物回复出错", e)
            e.printStackTrace()
            listener.onError(e)
        }
    }
    
    /**
     * 流式获取带图片信息的宠物回复
     * @param petType 当前选择的宠物类型
     * @param message 用户输入的消息
     * @param listener 流式响应监听器
     */
    suspend fun getPetResponseWithPictureInfoStreaming(petType: PetTypes, message: String, listener: com.example.chat.model.StreamResponseListener) {
        // 创建一个包装监听器，用于处理图片信息
        val wrapperListener = object : com.example.chat.model.StreamResponseListener {
            private val responseBuffer = StringBuilder()
            
            override fun onContent(content: String) {
                responseBuffer.append(content)
                listener.onContent(content)
            }
            
            override fun onComplete() {
                // 完成时，尝试解析图片信息
                val fullResponse = responseBuffer.toString()
                val pictureInfo = extractPictureInfo(fullResponse)
                
                // 将图片信息保存到最后一条消息中
                lastPictureInfo = pictureInfo.second
                
                listener.onComplete()
            }
            
            override fun onError(e: Exception) {
                listener.onError(e)
            }
        }
        
        // 调用流式API
        getPetResponseStreaming(petType, message, wrapperListener)
    }
    
    // 用于存储最后一次API返回的图片信息
    private var lastPictureInfo: PictureInfo? = null
    
    /**
     * 获取并清除最后的图片信息
     */
    fun consumeLastPictureInfo(): PictureInfo? {
        val info = lastPictureInfo
        lastPictureInfo = null
        return info
    }
    
    /**
     * 从响应中提取图片信息
     */
    private fun extractPictureInfo(response: String): Pair<String, PictureInfo> {
        val systemNoteStart = response.indexOf("<system_note>")
        val systemNoteEnd = response.indexOf("</system_note>")

        return if (systemNoteStart != -1 && systemNoteEnd != -1) {
            // 只返回系统指令之前的内容
            val cleanResponse = response.substring(0, systemNoteStart).trim()
            val jsonStr = response.substring(systemNoteStart + 13, systemNoteEnd)

            try {
                val pictureInfo = json.decodeFromString<PictureInfo>(jsonStr)
                Pair(cleanResponse, pictureInfo)
            } catch (e: Exception) {
                Pair(cleanResponse, PictureInfo(false, ""))
            }
        } else {
            // 如果没有找到系统指令，返回完整响应和空图片信息
            Pair(response, PictureInfo(false, ""))
        }
    }

    /**
     * 获取所有会话，按宠物类型分组
     * 每种宠物类型只返回一个会话，使用该宠物类型的最新消息作为会话信息
     */
    suspend fun getAllSessions(): List<PetChatViewModel.SessionInfo> {
        // 获取所有宠物类型
        val petTypes = PetTypes.values()
        
        // 为每种宠物类型创建一个会话
        return petTypes.map { petType ->
            // 获取该宠物类型的最新消息
            val latestMessages = chatDao.getRecentSessionMessages("default_${petType.name}", petType.name, 1)
            val lastMessage = if (latestMessages.isNotEmpty()) latestMessages[0].content else ""
            val timestamp = if (latestMessages.isNotEmpty()) latestMessages[0].timestamp else System.currentTimeMillis()
            
            // 创建会话信息
            PetChatViewModel.SessionInfo(
                sessionId = "default_${petType.name}",
                petType = petType,
                petName = getPetName(petType),
                lastMessage = lastMessage,
                timestamp = timestamp
            )
        }
    }

    private fun getPetName(petType: PetTypes): String {
        return when (petType) {
            PetTypes.CAT -> "布丁"
            PetTypes.DOG -> "大白"
            PetTypes.HAMSTER -> "团绒"
            PetTypes.DOG2 -> "豆豆"
        }
    }
}

