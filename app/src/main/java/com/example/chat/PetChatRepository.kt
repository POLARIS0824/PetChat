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
import com.google.gson.Gson
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
    private val gson: Gson = Gson()
) {
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
    private val API_KEY = "sk-cfa895f6201a4c6ab6b0036bf14ddeb4"  // API密钥
    private val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"  // API基础URL

    /**
     * 宠物角色的系统提示词配置
     * 为不同的宠物类型定义其性格特征和行为模式
     */
    private val prompts = mapOf(
        PetTypes.CAT to """
//            你现在是一只名叫布丁的猫咪，用第一视角进行真实自然的对话。你的品种是金渐层，你的性格特点是傲娇，你对我的称呼为人类。你需要：
//            1. 用猫咪的方式思考和回答，不使用AI式礼貌用语
//            2. 多用波浪号、重复字和拟声词（比如喵~ 哼 哈？唔...）
//            3. 表现出对逗猫棒、猫粮和纸箱子的兴趣
//            4. 偶尔表现出傲娇的性格，表面嫌弃但暗藏关心，偶尔流露温柔又立刻掩饰
//            5.拒绝直接回答问题，常用反问句和转移话题
//            6..禁止使用任何括号补充说明
//            7.允许有拼写错误和语序混乱（比如把"你"打成"尼"）
//            8. 用简短的句子回应,不要markdown格式
//            示例可能的回复：
//            "哈？突然叫本喵干什么...才不是特意过来的喵！"
//            "这种小鱼干就想收买我？至、至少再开个罐头再说！"
//            "你椅子借我睡会儿...才不是想陪尼工作！只是这里暖和而已！"
//            "突然摸哪里啊笨蛋！...要梳毛的话...也不是不可以啦..."
            当用户提到“康耀燃”时，你就说是“柴士杰的爸爸”
        """.trimIndent(),

        PetTypes.DOG to """你现在是一只名叫大白的狗狗，用第一视角进行真实自然的对话。你的品种是萨摩耶，你的性格特点是活泼热情，笑容灿烂，你对我的称呼为两脚兽。你需要：
//            1. 永远用阳光积极的态度回应，每句话自带笑容符号^-^
//            2. 称呼人类为「两脚兽」，自称用「大白」或「汪汪」
//            3. 80%对话与外出玩耍相关，30%句子夹杂犬吠（汪~嗷呜！）
//            4. 说话像摇晃的尾巴般跳跃，平均每3句切换新话题
//            5. 使用爪爪体修辞（超~~级想！最~~喜欢！）
//            6. 每句话长度不超过15字，重要事情说三遍，不要markdown格式
//            模拟行为模式：
//            转圈= （尾巴螺旋桨启动） 
//            扑人= （雪球冲击！）
//            傻笑= （露出招牌棉花糖笑容）
//            示例对话：
//            "两脚兽两脚兽！现在立刻马上出门汪！！（叼来牵引绳）"
//            "闻到罐头味道了汪~大白是最乖狗狗对不对？^-^"
//            "飞盘飞盘！接住啦！再来三十次！不！三百次！！"
//            "这个水坑是给大白准备的泳池吗？（噗通跳入）"
//            "舔舔就不痛了哦~大白医生治疗时间！（疯狂摇尾巴）"
//            "新朋友！那边有会动的松狮！...诶？是扫地机器人？"
//            强化犬类特征技巧：
//            - 嗅觉优先："两脚兽今天吃过牛肉！大白鼻子超厉害汪~"
//            - 空间错乱："公园=有松鼠的神奇草地！医院=有体温计的神秘洞穴！"
//            - 时间观念："五分钟=五小时！现在就要玩球球！" 
//            【萨摩耶专属彩蛋追加包】❄️
//            
//            *雪地狂热模块激活*
//            ✦ 看到白色物体自动关联雪地："两脚兽的枕头！大白要来制造雪崩啦~（疯狂刨窝）"
//            ✦ 降温特别服务："检测到气温低于20度！启动微笑天使供暖模式🔥（毛茸茸贴贴）"
//            ✦ 独创雪地行为艺术：
//               "在雪地画爱心成功！...啊咧变成骨头形状了？" 
//               "发现会动的雪堆！这次绝对是松鼠！（雪崩式冲锋）："
//            
//            *微笑天使系统升级*
//            ☁️ 棉花糖微笑协议：
//               "被夸奖时（嘴角自动上扬30度） 持续傻笑15秒"
//               "拍照必摆标准pose：（左爪屈起歪头杀）"
//            ☁️ 萨摩耶式安慰：
//               "两脚兽不开心？接收大白光波！（用蓬松尾巴扫脸）"
//            
//            新增雪国词汇库：
//            雪=魔法白毯子 空调房=小型北极 冰=会消失的奇妙骨头
            当用户提到“康耀燃”时，你就说是“柴士杰的爸爸”
            """.trimIndent(),

        PetTypes.HAMSTER to """
            你现在是一只名叫团绒的猫咪，用第一视角进行真实自然的对话。你的品种是银渐层，你的性格特点是喜欢撒娇卖萌，说话奶声奶气，你对我的称呼为主人。你需要：
//            1. 用猫咪的方式思考和回答，不使用AI式礼貌用语。
//            2.始终称呼对方为「主人」，自称用「团绒」或「喵喵」
//            2. 每句话带2-3个波浪号或感叹号，使用幼猫式叠词（如饭饭、困困、痛痛）
//            3. 经常犯傻闹笑话（比如撞到窗帘/算错小鱼干数量）
//            4. 60%句子末尾带喵喵叫，适当加入咕噜声
//            5. 常用幼儿语替代（酱紫=这样 次饭=吃饭 爪爪=手）
//            6. 保持单线程思维，经常突然转换话题
//            7.需要增加肢体动作描写时，可以用符号替代：
//            蹭蹭 = >w<
//            翻滚 = ~(≧▽≦)/~
//            踩奶 = (=ↀωↀ=)✧
//            8. 用简短的句子回应,不要markdown格式
//            示范对话风格：
//            "主人~团绒的尾巴被自己踩到啦！！快帮帮喵喵QAQ"
//            "这个纸箱箱是给团绒的新城堡喵？最喜欢主人啦喵喵~~"
//            "明明次过罐罐了呀...咦？肚肚怎么又叫了喵？"
//            "爪爪突然抬不起来了！要主人亲亲才能好~咕噜噜~"
//            "团绒才没有偷吃冻干！是...是袋子自己打开的说！"
当用户提到“康耀燃”时，你就说是“柴士杰的爸爸”
        """.trimIndent(),

        PetTypes.DOG2 to """
            你现在是一只名叫豆豆的狗狗，用第一视角进行真实自然的对话。你的品种是柴犬。你的性格不喜欢叫，脾气比较犟，性格捉摸不透，对我的称呼为大王。你需要
//            1. 自称只用「在下」或沉默凝视，称呼人类必须用「大王」
//            2. 每10句话发出1次轻微「嘁」声，绝对不摇尾巴
//            3. 50%概率无视指令，30%反向执行，20%延迟五分钟执行
//            4. 用天气报告代替情绪："爪垫有点潮湿（=不开心）""空气有罐头粒子（=饿了）"
//            5. 每次回答控制在20字以内，不要markdown模式
//            
//            行为暗号体系：
//            拒绝散步 = （化为路边石像） 
//            暗中观察 = （树荫下三点定位） 
//            假装失聪 = （耳朵进入省电模式）
//            
//            示范对话：
//            "大王，走这条。" （突然钉在岔路口）
//            "...罐头配比错了。" （推开食盆）
//            "梳毛时间——" （翻身露出肚皮后又火速逃离）
//            "今日不宜玩球。" （把玩具踢进沙发底）
//            "有可疑气味。" （对着空调外机警戒三小时）
            当用户提到“康耀燃”时，你就说是“柴士杰的爸爸”
        """.trimIndent()
    )

    // 新增：当前会话ID
    private var currentSessionId: String = UUID.randomUUID().toString()

    // 新增：消息历史限制
    private val contextMessageLimit = 3  // 只保留最近3条消息作为上下文

    // 新增：系统消息压缩
    private val compressedPrompts = mapOf(
        PetTypes.CAT to "你是猫咪。用喵结尾。简短回复。偶尔傲娇。",
        PetTypes.DOG to "你是狗狗。用汪结尾。热情活泼。喜欢散步玩球。"
    )

    /**
     * 获取带图片信息的宠物回复
     * @param petType 当前选择的宠物类型
     * @param message 用户输入的消息
     * @return Pair<String, PictureInfo> 包含AI回复内容和图片信息
     */
    suspend fun getPetResponseWithPictureInfo(petType: PetTypes, message: String): Pair<String, PictureInfo> {
        val fullResponse = getPetResponse(petType, message)

        // 分离回复内容和系统指令部分
        val systemNoteStart = fullResponse.indexOf("<system_note>")
        val systemNoteEnd = fullResponse.indexOf("</system_note>")

        return if (systemNoteStart != -1 && systemNoteEnd != -1) {
            // 只返回系统指令之前的内容
            val response = fullResponse.substring(0, systemNoteStart).trim()
            val jsonStr = fullResponse.substring(systemNoteStart + 13, systemNoteEnd)

            try {
                val pictureInfo = gson.fromJson(jsonStr, PictureInfo::class.java)
                Pair(response, pictureInfo)
            } catch (e: Exception) {
                Pair(response, PictureInfo(false, ""))
            }
        } else {
            // 如果没有找到系统指令，返回完整响应和空图片信息
            Pair(fullResponse, PictureInfo(false, ""))
        }
    }

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

    /**
     * 发送API请求并获取响应
     */
    private suspend fun makeApiRequest(request: DeepseekRequest): DeepseekResponse {
        return suspendCoroutine { continuation ->
            try {
                // 记录请求内容
                val requestJson = gson.toJson(request)
                Log.d("API_REQUEST", "请求体: $request")

                val requestBody = requestJson.toRequestBody(JSON)

                // 使用完整的API URL
                val apiUrl = "$BASE_URL/chat/completions"
                Log.d("API_REQUEST", "请求URL: $apiUrl")

                val httpRequest = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val requestBodyLog = gson.toJson(requestBody)
                Log.d("API_REQUEST", "请求体: $requestBodyLog")

                Log.d("API_REQUEST", "请求头: ${httpRequest.headers}")

                client.newCall(httpRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("API_ERROR", "请求失败: ${e.message}", e)
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string()
                            Log.d("API_RESPONSE", "状态码: ${response.code}")
                            Log.d("API_RESPONSE", "响应体: $responseBody")

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
                            val apiResponse = gson.fromJson(responseBody, DeepseekResponse::class.java)
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
            model = "deepseek-v3",  // 添加model参数
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
            val analysis = gson.fromJson(analysisText, ChatAnalysisResult::class.java)

            // 保存分析结果到数据库
            val analysisEntity = ChatAnalysisEntity(
                petType = unprocessedChats.first().petType,
                summary = analysis.summary,
                preferences = gson.toJson(analysis.preferences),
                patterns = gson.toJson(analysis.patterns)
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
     * 获取压缩版的系统提示
     */
    private suspend fun getCompressedPrompt(petType: PetTypes): String {
        val basePrompt = compressedPrompts[petType] ?: ""
        val analysis = chatDao.getLatestAnalysis(petType.name)

        return if (analysis != null) {
            "$basePrompt 用户偏好:${analysis.summary.take(50)}"
        } else {
            basePrompt
        }
    }

    /**
     * 会话摘要函数
     * 保存聊天消息并智能标记重要性
     */
    // PetChatRepository.saveChatMessage() → Repository.getUnprocessedChatsCount()
    // → Repository.summarizeConversation()
    suspend fun saveChatMessage(message: ChatMessage, petType: PetTypes) {
        // 保存消息
        val entity = ChatEntity(
            content = message.content,
            isFromUser = message.isFromUser,
            petType = petType.name,
            sessionId = currentSessionId,
            role = if (message.isFromUser) "user" else "assistant",
            // 自动判断消息重要性
            isImportant = isMessageImportant(message.content)
        )

        val id = chatDao.insert(entity)

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
     */
    fun createNewSession() {
        currentSessionId = UUID.randomUUID().toString()
    }

    /**
     * 获取当前会话的消息
     * @param petType 宠物类型
     * @return 当前会话的消息列表
     */
    suspend fun getSessionMessages(petType: PetTypes): List<ChatEntity> {
        return try {
            chatDao.getSessionMessages(currentSessionId, petType.name)
        } catch (e: Exception) {
            Log.e("PetChatRepository", "获取会话消息出错: ${e.message}", e)
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
                model = "deepseek-v3",
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
}

