package com.example.chat.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.chat.ChatBubble
import com.example.chat.ChatInput
import com.example.chat.PetChatViewModel
import com.example.chat.R
import com.example.chat.model.ChatUiState
import com.example.chat.model.PetTypes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: PetChatViewModel,
    petType: PetTypes,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showPetSelector: Boolean = false,
    onHidePetSelector: () -> Unit = {}
) {
    var showSettings by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val uiState by viewModel.chatUiState.collectAsState()
    val state = (uiState as? ChatUiState.Ready) ?: ChatUiState.Ready()

    LaunchedEffect(petType) {
        if (state.chatHistory.isNotEmpty()) {
            delay(300)
            listState.animateScrollToItem(state.chatHistory.size - 1)
        }
    }

    val emptyStateImages = listOf(
        R.drawable.greeting,
        R.drawable.greeting2,
        R.drawable.greeting3,
    )
    val randomImageRes = remember { emptyStateImages.random() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LaunchedEffect(petType) {
                viewModel.selectPetType(petType)
            }

            if (state.chatHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            enabled = showPetSelector,
                            onClick = onHidePetSelector,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = randomImageRes),
                            contentDescription = "没有消息",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            text = "开始和宠物聊天吧！",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            enabled = showPetSelector,
                            onClick = onHidePetSelector,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(
                        items = state.chatHistory,
                        key = { it.hashCode() }
                    ) { msg ->
                        val isCurrentlyStreaming = state.isStreaming &&
                                state.streamingMessage != null &&
                                !msg.isFromUser &&
                                msg == state.chatHistory.lastOrNull { !it.isFromUser }

                        ChatBubble(
                            message = msg,
                            modifier = Modifier.animateItem(),
                            isStreaming = isCurrentlyStreaming
                        )
                    }
                }

                LaunchedEffect(state.shouldScrollToBottom, state.chatHistory.size) {
                    if ((state.shouldScrollToBottom || state.chatHistory.isNotEmpty()) && state.chatHistory.isNotEmpty()) {
                        listState.animateScrollToItem(state.chatHistory.size - 1)

                        val lastMessage = state.chatHistory.last()
                        val extraScrollDistance = if (!lastMessage.isFromUser) {
                            val contentLength = lastMessage.content.length
                            (200f + contentLength * 0.5f).coerceAtMost(500f)
                        } else {
                            100f
                        }

                        delay(150)
                        listState.scrollBy(extraScrollDistance)

                        if (state.isStreaming && !lastMessage.isFromUser) {
                            delay(100)
                            listState.scrollToItem(state.chatHistory.size - 1)
                        }
                    }
                }

                LaunchedEffect(state.streamingMessage) {
                    state.streamingMessage?.let { msg ->
                        if (!msg.isFromUser && msg.content.isNotEmpty()) {
                            delay(100)
                            listState.animateScrollToItem(state.chatHistory.size - 1)
                        }
                    }
                }
            }

            ChatInput(
                message = message,
                onMessageChange = { message = it },
                onSendClick = {
                    if (message.isNotEmpty()) {
                        viewModel.sendMessage(message)
                        message = ""
                        coroutineScope.launch {
                            listState.animateScrollToItem(viewModel.getChatHistory(petType).size - 1)
                        }
                    }
                },
                isLoading = state.isForegroundLoading,
                isStreaming = state.isStreaming,
                onFocusChanged = { isFocused ->
                    if (isFocused && state.chatHistory.isNotEmpty()) {
                        viewModel.resetScroll()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .consumeWindowInsets(contentPadding)
                    .imePadding()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        viewModel.resetScroll()
                    }
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("设置") },
            text = { Text("设置选项将在这里显示") },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("确定")
                }
            }
        )
    }
}
