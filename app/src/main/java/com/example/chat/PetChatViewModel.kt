package com.example.chat

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.ChatDatabase
import com.example.chat.model.ChatMessage
import com.example.chat.model.PetTypes
import com.example.chat.model.PictureInfo
import com.example.chat.model.StreamResponseListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 宠物聊天的ViewModel
 * 负责管理UI状态和处理用户交互
 */
class PetChatViewModel(application: Application) : AndroidViewModel(application) {
    // 当前会话ID
    private var currentSessionId: String = "default"
    // 初始化Repository，传入数据库DAO
    private val repository: PetChatRepository = PetChatRepository.getInstance(
        ChatDatabase.getDatabase(application).chatDao()
    )

    private val _isForegroundLoading = mutableStateOf(false)
    val isForegroundLoading: Boolean get() = _isForegroundLoading.value

    // 当前选择的宠物类型，默认为猫咪
    private var currentPetType by mutableStateOf(PetTypes.CAT)
        private set

    // 聊天历史记录列表
    var chatHistory by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    // 最后一次AI返回的图片信息
    private var lastPictureInfo: PictureInfo? = null
        private set

    // 添加加载状态
    var isLoading by mutableStateOf(false)
        private set
        
    // 流式传输状态
    var isStreaming by mutableStateOf(false)
        private set
        
    // 当前正在流式传输的消息
    var streamingMessage by mutableStateOf<ChatMessage?>(null)
        private set

    // 添加一个可观察的状态来触发滚动
    private var _shouldScrollToBottom = mutableStateOf(false)
    val shouldScrollToBottom: Boolean by _shouldScrollToBottom

    init {
        loadChatHistory()
    }

    /**
     * 从数据库加载聊天历史
     * 按宠物类型加载，不考虑会话ID
     */
    private fun loadChatHistory() {
        viewModelScope.launch {
            try {
                // 使用按宠物类型查询的方法，不再使用会话ID
                val messages = repository.getMessagesByPetType(currentPetType)
                chatHistory = messages.map { entity ->
                    ChatMessage(
                        content = entity.content,
                        isFromUser = entity.isFromUser,
                        petType = PetTypes.valueOf(entity.petType)
                    )
                }
                
                // 加载完成后触发滚动到底部
                if (chatHistory.isNotEmpty()) {
                    _shouldScrollToBottom.value = false
                    delay(100) // 短暂延迟确保UI更新
                    _shouldScrollToBottom.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 切换当前的宠物类型
     */
    fun selectPetType(petType: PetTypes) {
        currentPetType = petType
        loadChatHistory()
    }

    /**
     * 创建新会话
     */
    fun createNewSession() {
        viewModelScope.launch {
            val newSessionId = repository.createNewSession()
            currentSessionId = newSessionId
            chatHistory = emptyList() // 清空当前聊天历史
            // 加载所有会话列表
            loadAllSessions()
        }
    }

    /**
     * 核心函数
     * 发送新消息
     * 处理用户输入，获取AI响应，并更新UI状态
     */
    // PetChatViewModel.sendMessage() → Repository.getUnprocessedChatsCount() →
    // Repository.analyzeChats() → PetChatRepository.makeApiRequest()
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        // 默认使用流式传输方式
        sendMessageStreaming(message)
    }
    
    /**
     * 使用流式传输发送消息
     * 处理用户输入，流式获取AI响应，并实时更新UI状态
     */
    private fun sendMessageStreaming(message: String) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            // 设置前台和通用加载状态为true
            _isForegroundLoading.value = true
            isLoading = true
            isStreaming = true

            try {
                // 添加用户消息
                val userMessage = ChatMessage(
                    content = message,
                    isFromUser = true,
                    petType = currentPetType
                )
                chatHistory = chatHistory + userMessage
                repository.saveChatMessage(userMessage, currentPetType)
                
                // 创建一个空的AI回复消息，用于流式更新
                val petMessage = ChatMessage(
                    content = "",
                    isFromUser = false,
                    petType = currentPetType
                )
                
                // 设置为当前流式消息
                streamingMessage = petMessage
                // 添加到聊天历史
                chatHistory = chatHistory + petMessage
                
                // 触发滚动到底部 - 使用延迟触发确保UI更新后再滚动
                _shouldScrollToBottom.value = false
                viewModelScope.launch {
                    delay(50) // 短暂延迟
                    _shouldScrollToBottom.value = true
                }
                
                // 创建流式响应监听器
                val responseListener = object : StreamResponseListener {
                    private val responseBuffer = StringBuilder()
                    
                    override fun onContent(content: String) {
                        // 更新流式消息内容
                        responseBuffer.append(content)
                        val updatedMessage = petMessage.copy(content = responseBuffer.toString())
                        streamingMessage = updatedMessage
                        
                        // 更新聊天历史中的最后一条消息
                        chatHistory = chatHistory.dropLast(1) + updatedMessage
                        
                        // 触发滚动到底部
                        // 先设置为false再设置为true，确保每次都能触发LaunchedEffect
                        _shouldScrollToBottom.value = false
                        viewModelScope.launch {
                            delay(50) // 短暂延迟，确保UI更新后再触发滚动
                            _shouldScrollToBottom.value = true
                        }
                    }
                    
                    override fun onComplete() {
                        // 流式传输完成
                        isStreaming = false
                        
                        // 获取最终消息内容
                        val finalContent = responseBuffer.toString()
                        val finalMessage = petMessage.copy(content = finalContent)
                        
                        // 保存最终消息到数据库
                        viewModelScope.launch {
                            repository.saveChatMessage(finalMessage, currentPetType)
                            
                            // 获取图片信息
                            val pictureInfo = repository.consumeLastPictureInfo()
                            if (pictureInfo != null) {
                                lastPictureInfo = pictureInfo
                            }
                            
                            // 检查是否需要进行分析
                            val unprocessedCount = repository.getUnprocessedChatsCount()
                            if (unprocessedCount >= 10) {
                                repository.analyzeChats()
                            }
                        }
                        
                        // 清除流式消息引用
                        streamingMessage = null
                        
                        // 最后一次触发滚动到底部，确保显示完整消息
                        _shouldScrollToBottom.value = false
                        viewModelScope.launch {
                            delay(100) // 较长的延迟，确保UI完全更新
                            _shouldScrollToBottom.value = true
                        }
                        
                        // 前台请求完成，关闭前台加载状态
                        _isForegroundLoading.value = false
                        isLoading = false
                    }
                    
                    override fun onError(e: Exception) {
                        // 处理错误
                        e.printStackTrace()
                        
                        // 更新消息为错误提示
                        val errorMessage = petMessage.copy(content = "抱歉，我现在有点累了，待会再聊吧。")
                        streamingMessage = errorMessage
                        chatHistory = chatHistory.dropLast(1) + errorMessage
                        
                        // 保存错误消息到数据库
                        viewModelScope.launch {
                            repository.saveChatMessage(errorMessage, currentPetType)
                        }
                        
                        // 重置状态
                        isStreaming = false
                        _isForegroundLoading.value = false
                        isLoading = false
                        streamingMessage = null
                    }
                }
                
                // 调用流式API
                repository.getPetResponseWithPictureInfoStreaming(currentPetType, message, responseListener)
                
            } catch (e: Exception) {
                e.printStackTrace()
                isStreaming = false
                _isForegroundLoading.value = false
                isLoading = false
                streamingMessage = null
            }
        }
    }
    
    /**
     * 非流式方式发送消息（保留原方法作为备用）
     */
    fun sendMessageNonStreaming(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            // 设置前台和通用加载状态为true
            _isForegroundLoading.value = true
            isLoading = true

            try {
                // 添加用户消息
                val userMessage = ChatMessage(
                    content = message,
                    isFromUser = true,
                    petType = currentPetType
                )
                chatHistory = chatHistory + userMessage
                repository.saveChatMessage(userMessage, currentPetType)

                // 获取AI响应 - 这是前台请求
                val (response, pictureInfo) = repository.getPetResponseWithPictureInfo(currentPetType, message)
                val petMessage = ChatMessage(
                    content = response,
                    isFromUser = false,
                    petType = currentPetType
                )
                chatHistory = chatHistory + petMessage
                repository.saveChatMessage(petMessage, currentPetType)

                // 更新图片信息
                lastPictureInfo = pictureInfo

                // 前台请求完成，关闭前台加载状态
                _isForegroundLoading.value = false

                // 触发滚动到底部
                _shouldScrollToBottom.value = true

                // 检查是否需要进行分析 - 这是后台任务，不影响前台加载状态
                val unprocessedCount = repository.getUnprocessedChatsCount()
                if (unprocessedCount >= 10) {
                    repository.analyzeChats()
                } else {
                    isLoading = false
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _isForegroundLoading.value = false
                isLoading = false
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 重置滚动状态并触发滚动到底部
     */
    fun resetScroll() {
        viewModelScope.launch {
            _shouldScrollToBottom.value = false
            delay(100) // 短暂延迟确保UI更新
            _shouldScrollToBottom.value = true
        }
    }

    /**
     * 获取并清除最后的图片信息
     * 使用后即清除，确保图片信息只被使用一次
     */
    fun consumeLastPictureInfo(): PictureInfo? {
        val info = lastPictureInfo
        lastPictureInfo = null
        return info
    }

    // 获取指定宠物类型的聊天历史
    fun getChatHistory(petType: PetTypes): List<ChatMessage> {
        return try {
            // 如果当前选择的宠物类型与请求的类型相同，直接返回缓存的聊天历史
            if (currentPetType == petType) {
                chatHistory
            } else {
                // 切换宠物类型并触发加载
                selectPetType(petType)
                // 返回空列表，等待 loadChatHistory 完成后会自动更新 UI
                return emptyList()
            }
        } catch (e: Exception) {
            // 记录错误并返回空列表
            Log.e("PetChatViewModel", "获取聊天历史出错: ${e.message}", e)
            emptyList()
        }
    }
    
    // 会话信息数据类
    data class SessionInfo(
        val sessionId: String,
        val petType: PetTypes,
        val petName: String,
        val lastMessage: String,
        val timestamp: Long
    )
    
    // 获取所有会话列表
    private val _allSessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val allSessions: StateFlow<List<SessionInfo>> = _allSessions
    
    // 加载所有会话
    fun loadAllSessions() {
        viewModelScope.launch {
            val sessions = repository.getAllSessions()
            _allSessions.value = sessions
        }
    }
    
    // 切换到指定宠物类型的会话
    fun switchToSession(sessionId: String) {
        // 从会话实体中提取宠物类型
        viewModelScope.launch {
            // 查找对应的会话信息
            val session = _allSessions.value.find { it.sessionId == sessionId }
            if (session != null) {
                // 切换到对应的宠物类型
                selectPetType(session.petType)
            }
        }
    }
}