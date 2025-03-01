package com.example.chat

import android.app.Application
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

    // 当前选择的宠物类型，默认为猫咪
    var currentPetType by mutableStateOf(PetTypes.CAT)
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

    /**
     * 切换当前的宠物类型
     */
    fun selectPetType(petType: PetTypes) {
        currentPetType = petType
    }

    /**
     * 发送新消息
     * 处理用户输入，获取AI响应，并更新UI状态
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
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

                // 获取AI响应
                val (response, pictureInfo) = repository.getPetResponseWithPictureInfo(currentPetType, message)
                val petMessage = ChatMessage(
                    content = response,
                    isFromUser = false,
                    petType = currentPetType
                )
                chatHistory = chatHistory + petMessage
                repository.saveChatMessage(petMessage, currentPetType)
                
                // 检查是否需要进行分析
                if (repository.getUnprocessedChatsCount() >= 10) {
                    repository.analyzeChats()
                }
                
                // 触发滚动到底部
                _shouldScrollToBottom.value = true
                
            } catch (e: Exception) {
                e.printStackTrace()
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

    fun getChatHistory(petType: PetTypes): List<ChatMessage> {
        return chatHistory.filter { it.petType == petType }
    }
}