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
import kotlinx.coroutines.launch

/**
 * 宠物聊天的ViewModel
 * 负责管理UI状态和处理用户交互
 */
class PetChatViewModel(application: Application) : AndroidViewModel(application) {
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

    // 添加一个可观察的状态来触发滚动
    private var _shouldScrollToBottom = mutableStateOf(false)
    val shouldScrollToBottom: Boolean by _shouldScrollToBottom

    init {
        loadChatHistory()
    }

    /**
     * 从数据库加载聊天历史
     */
    private fun loadChatHistory() {
        viewModelScope.launch {
            try {
                val messages = repository.getSessionMessages(currentPetType)
                chatHistory = messages.map { entity ->
                    ChatMessage(
                        content = entity.content,
                        isFromUser = entity.isFromUser,
                        petType = PetTypes.valueOf(entity.petType)
                    )
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
            repository.createNewSession()
            chatHistory = emptyList() // 清空当前聊天历史
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
     * 重置滚动状态
     */
    fun resetScroll() {
        _shouldScrollToBottom.value = false
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
}