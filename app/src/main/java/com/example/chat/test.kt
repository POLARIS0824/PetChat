package com.example.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun BlurTestCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "模糊效果测试",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // 测试1：图片底部区域模糊 - 正确实现
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 图片
                Image(
                    painter = painterResource(id = R.drawable.pet_cat),
                    contentDescription = "图片底部区域模糊",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // 底部区域的模糊效果 - 先放背景再模糊
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f)
                                )
                            )
                        )
                        .blur(radius = 15.dp)
                )

                // 文本内容 - 放在最上层保持清晰
                Text(
                    text = "正确的底部模糊效果",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        // 测试2：整体模糊 - 修复高度问题
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pet_cat),
                    contentDescription = "整体模糊",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.7f)
                                )
                            )
                        )
                ) {
                    Text(
                        text = "整体模糊 (10.dp)",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // 测试3：只模糊图片，文字保持清晰
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 模糊的图片
                Image(
                    painter = painterResource(id = R.drawable.pet_cat),
                    contentDescription = "模糊的图片",
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 8.dp),
                    contentScale = ContentScale.Crop
                )

                // 清晰的文字
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f)
                                )
                            )
                        )
                ) {
                    Text(
                        text = "模糊的图片，清晰的文字",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        // 测试4：只模糊底部区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 清晰的图片
                Image(
                    painter = painterResource(id = R.drawable.pet_cat),
                    contentDescription = "底部区域模糊",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // 底部区域 - 使用裁剪和模糊
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    // 裁剪的图片部分，应用模糊
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(radius = 15.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.4f)
                                    )
                                )
                            )
                    )

                    // 清晰的文字
                    Text(
                        text = "只模糊底部区域",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BlurTestPreview() {
    MaterialTheme {
        BlurTestCard()
    }
}