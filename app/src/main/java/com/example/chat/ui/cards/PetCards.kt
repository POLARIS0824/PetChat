package com.example.chat.ui.cards

import android.graphics.RenderEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.example.chat.R
import com.example.chat.model.Pet
import com.example.chat.model.PetTypes
import kotlin.math.roundToInt

@Composable
fun PetList(
    pets: List<Pet>,
    modifier: Modifier = Modifier,
    onNavigateToChat: (PetTypes) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(pets) { pet ->
            PetCard(
                pet = pet,
                onChatClick = onNavigateToChat
            )
        }
    }
}

@Composable
fun PetCard(
    pet: Pet,
    onChatClick: (PetTypes) -> Unit = {}
) {
    val maxDragDistance = 200.dp
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { maxDragDistance.toPx() }

    val initialVisiblePercentage = 0f
    var visiblePercentage by remember { mutableFloatStateOf(initialVisiblePercentage) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val startOffsetY = cardSize.height * 0.67f
    val maxOffsetY = cardSize.height * 0.5f
    val offsetY = startOffsetY - (startOffsetY * visiblePercentage).coerceIn(0f, maxOffsetY)
    val blurRadius = (50f * visiblePercentage).coerceAtLeast(0.01f)

    LaunchedEffect(Unit) {
        visiblePercentage = initialVisiblePercentage
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .onSizeChanged { cardSize = it },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            visiblePercentage = if (visiblePercentage < 0.5f) 0f else 1f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dragDelta = -dragAmount.y / maxOffsetPx
                            visiblePercentage = (visiblePercentage + dragDelta).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            Image(
                painter = painterResource(id = pet.initialRes),
                contentDescription = "Pet Image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = RenderEffect
                            .createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.DECAL)
                            .asComposeRenderEffect()
                    },
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .offset { IntOffset(0, offsetY.roundToInt()) }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.blur),
                    contentDescription = "Blur Overlay",
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .align(Alignment.TopCenter)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.06f),
                            end = min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.06f),
                            top = min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.06f),
                            bottom = min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.06f)
                        )
                ) {
                    Text(
                        text = pet.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Text(
                        text = pet.status,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.05f)))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        InfoTag(
                            text = "${pet.breed}·雄性",
                            backgroundColor = Color(0xFFD8F0D7),
                            modifier = Modifier.padding(end = 24.dp)
                        )

                        InfoTag(
                            text = "28kg",
                            backgroundColor = Color(0xFFF0C0BD),
                            modifier = Modifier.padding(end = 24.dp)
                        )

                        InfoTag(
                            text = "${pet.age}",
                            backgroundColor = Color(0xFFF0E4BD)
                        )
                    }

                    Spacer(modifier = Modifier.height(min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.04f)))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(end = 24.dp)
                                .weight(1f)
                                .width(160.dp)
                        ) {
                            Text("删除", color = Color.Black)
                        }

                        Button(
                            onClick = { onChatClick(pet.petType) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(255, 166, 88)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .width(160.dp)
                                .weight(1f)
                        ) {
                            Text("去对话", color = Color.White)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 32.dp, end = 16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.background_icon),
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            contentScale = ContentScale.Crop
                        )

                        Icon(
                            painter = painterResource(id = R.drawable.card_icon),
                            contentDescription = "表情",
                            tint = Color.Black,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTag(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentSize()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(10)
            )
            .padding(
                horizontal = min(12.dp, LocalConfiguration.current.screenWidthDp.dp * 0.03f),
                vertical = min(8.dp, LocalConfiguration.current.screenWidthDp.dp * 0.02f)
            )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
