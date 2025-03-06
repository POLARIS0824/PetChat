package com.example.chat.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chat.R
import com.example.chat.model.SocialPost
import com.example.chat.ui.social.SocialViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    viewModel: SocialViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val posts = viewModel.posts
    var showAddPostDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(255,255,255))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp) // 为底部导航栏留出空间
        ) {
            items(posts) { post ->
                SocialPostItem(
                    post = post,
                    onLikeClick = { viewModel.likePost(post.id) },
                    onSaveClick = { viewModel.savePost(post.id) }
                )
                Divider(
                    color = Color(0xFFEEEEEE),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // 添加帖子按钮
        FloatingActionButton(
            onClick = { showAddPostDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(255, 143, 45),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "发布动态")
        }
    }

    // 添加帖子对话框
    if (showAddPostDialog) {
        AddPostDialog(
            onDismiss = { showAddPostDialog = false },
            onPost = { content ->
                viewModel.addPost(content)
                showAddPostDialog = false
            }
        )
    }
}

@Composable
fun SocialPostItem(
    post: SocialPost,
    onLikeClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // 用户信息行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 用户头像
            Image(
                painter = painterResource(id = post.authorAvatar),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 用户名和用户ID
            Column {
                Text(
                    text = post.authorName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = post.authorUsername,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }

        // 帖子内容
        Text(
            text = post.content,
            modifier = Modifier.padding(vertical = 12.dp),
            fontSize = 16.sp,
            lineHeight = 24.sp
        )

        // 时间戳
        Text(
            text = formatDate(post.timestamp),
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 交互按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            // 点赞按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLikeClick) {
                    Icon(
                        painter = painterResource(
                            id = if (post.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart
                        ),
                        contentDescription = "点赞",
                        tint = if (post.isLiked) Color(0xFFFF4D4D) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = post.likeCount.toString(),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            // 评论按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* 打开评论 */ }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_comment),
                        contentDescription = "评论",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = post.commentCount.toString(),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            // 收藏按钮
            IconButton(onClick = onSaveClick) {
                Icon(
                    painter = painterResource(
                        id = R.drawable.ic_bookmark
                    ),
                    contentDescription = "收藏",
                    tint = if (post.isSaved) Color(255, 143, 45) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AddPostDialog(
    onDismiss: () -> Unit,
    onPost: (String) -> Unit
) {
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发布新动态") },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("分享你的宠物趣事...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )
        },
        confirmButton = {
            Button(
                onClick = { onPost(content) },
                enabled = content.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(255, 143, 45)
                )
            ) {
                Text("发布")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatDate(date: Date): String {
    val now = Calendar.getInstance()
    val postTime = Calendar.getInstance().apply { time = date }

    return when {
        now.get(Calendar.YEAR) != postTime.get(Calendar.YEAR) -> {
            SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()).format(date)
        }
        now.get(Calendar.DAY_OF_YEAR) != postTime.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(date)
        }
        else -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
    }
}

// 写一个 preview
@Preview
@Composable
fun PreviewSocialScreen() {
    SocialScreen()
}
