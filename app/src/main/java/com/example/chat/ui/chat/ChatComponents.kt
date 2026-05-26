package com.example.chat.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.chat.ui.theme.AccentOrange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chat.R
import com.example.chat.model.ChatMessage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun AnimatedAvatar(
    frameResIds: List<Int>,
    modifier: Modifier = Modifier,
    frameDelay: Long = 150L
) {
    var currentFrame by remember { mutableStateOf(0) }

    val transition = updateTransition(
        targetState = currentFrame,
        label = "Avatar Animation"
    )

    val alpha by transition.animateFloat(
        label = "Alpha",
        transitionSpec = { tween(frameDelay.toInt() / 2) }
    ) { state ->
        if (state >= 0) 1f else 1f
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(frameDelay)
            currentFrame = (currentFrame + 1) % frameResIds.size
        }
    }

    androidx.compose.foundation.Image(
        painter = painterResource(id = frameResIds[currentFrame]),
        contentDescription = null,
        modifier = modifier
            .clip(CircleShape)
            .alpha(alpha),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val isFromUser = message.role == "user"
    val backgroundColor = if (isFromUser) AccentOrange else Color.White
    val textColor = if (isFromUser) Color.White else Color.Black
    val arrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    val bubbleShape = if (isFromUser) {
        androidx.compose.foundation.shape.RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = timeFormat.format(message.timestamp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = arrangement
    ) {
        Column(horizontalAlignment = if (isFromUser) Alignment.End else Alignment.Start) {
            androidx.compose.material3.Surface(
                shape = bubbleShape,
                color = backgroundColor,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.content,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        if (isStreaming && !isFromUser) {
                            Box(modifier = Modifier.size(width = 24.dp, height = 16.dp)) {
                                TypingIndicator()
                            }
                        }
                    }

                    Text(
                        text = timeString,
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val dotSize = 4.dp
    val dotColor = Color.Gray
    val animationDuration = 1000
    val delayBetweenDots = 200

    Row(modifier = modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val infiniteTransition = rememberInfiniteTransition(label = "")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(animationDuration),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(delayBetweenDots * i)
                ),
                label = ""
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .size(dotSize)
                    .alpha(alpha)
                    .background(dotColor, CircleShape)
            )
        }
    }
}

@Composable
fun LoadingAnimation(
    modifier: Modifier = Modifier,
    dotSize: Float = 36f,
    dotColor: Color = AccentOrange,
    animationDuration: Int = 1000,
    delayBetweenDots: Int = 200
) {
    val maxOffset = 8f

    val infiniteTransitions = (0 until 4).map { rememberInfiniteTransition(label = "") }
    val offsets = infiniteTransitions.mapIndexed { index, it ->
        it.animateFloat(
            initialValue = 0f,
            targetValue = -maxOffset,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = animationDuration
                    0f at 0
                    maxOffset at animationDuration / 4
                    maxOffset at animationDuration * 3 / 4
                    0f at animationDuration
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(delayBetweenDots * index)
            ),
            label = ""
        )
    }

    Canvas(modifier = modifier) {
        val center = size.width / 2
        val dotSpacing = dotSize * 1.5f
        val startX = center - (dotSpacing * 1.5f)

        for (i in 0 until 4) {
            drawCircle(
                color = dotColor,
                radius = dotSize / 2,
                center = Offset(startX + i * dotSpacing, size.height / 2 + offsets[i].value.dp.toPx())
            )
        }
    }
}

@Composable
fun ChatInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    showPetSelector: Boolean = false,
    onHidePetSelector: () -> Unit = {},
    isStreaming: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(
                enabled = showPetSelector,
                onClick = onHidePetSelector,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color.White)
                .height(IntrinsicSize.Min)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { },
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more),
                    contentDescription = "Add",
                    tint = Color.Gray
                )
            }

            TextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .clickable(
                        enabled = showPetSelector,
                        onClick = onHidePetSelector,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    .onFocusChanged { focusState -> onFocusChanged(focusState.isFocused) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                placeholder = {
                    Text(
                        stringResource(R.string.chat_message_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(247, 247, 252),
                    unfocusedContainerColor = Color(247, 247, 252),
                    disabledContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            IconButton(
                onClick = onSendClick,
                modifier = Modifier.padding(start = 4.dp),
                enabled = message.isNotEmpty() && !isStreaming
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send),
                    contentDescription = "Send",
                    tint = AccentOrange
                )
            }
        }
    }
}

@Preview
@Composable
fun ChatInputPreview() {
    MaterialTheme {
        ChatInput(
            message = "Hello",
            onMessageChange = { },
            onSendClick = { },
            isLoading = false,
            onFocusChanged = { }
        )
    }
}

@Preview
@Composable
fun ChatInputLoadingPreview() {
    MaterialTheme {
        ChatInput(
            message = "",
            onMessageChange = { },
            onSendClick = { },
            isLoading = true,
            onFocusChanged = { }
        )
    }
}
