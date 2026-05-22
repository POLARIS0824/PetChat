package com.example.chat.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.example.chat.R
import com.example.chat.data.repository.SettingsManager
import com.example.chat.model.UserProfile
import com.example.chat.ui.theme.AccentOrange
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit = {},
) {
    BackHandler {
        onBack()
    }
    val initialProfile = remember { settingsManager.getUserProfile() }
    
    var username by remember { mutableStateOf(initialProfile.username) }
    var signature by remember { mutableStateOf(initialProfile.signature) }
    var selectedAvatarResId by remember { mutableIntStateOf(initialProfile.avatarResId) }
    
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(2000)
            showSuccess = false
        }
    }

    val avatarOptions = listOf(
        R.drawable.avatar1,
        R.drawable.avatar2,
        R.drawable.avatar3,
        R.drawable.avatar4
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "偏好设置",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color(0xFFF7F8FA)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Section Title: Preview
            Text(
                text = "卡片预览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Premium Real-time Preview Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    AccentOrange,
                                    Color(0xFFFFB366)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = selectedAvatarResId),
                            contentDescription = "Preview Avatar",
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(18.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = username.ifBlank { "Mrh Raju" },
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Verified",
                                        tint = AccentOrange,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = signature.ifBlank { "在云朵里养宠物，是生活的小惊喜" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            // Section Title: Select Avatar
            Text(
                text = "选择头像",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Horizontal Avatar Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                avatarOptions.forEach { avatarRes ->
                    val isSelected = selectedAvatarResId == avatarRes
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .scale(if (isSelected) 1.05f else 1.0f)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) AccentOrange else Color.LightGray.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { selectedAvatarResId = avatarRes },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = avatarRes),
                            contentDescription = "Avatar Option",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(AccentOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section Title: Edit Profile Information
            Text(
                text = "编辑资料",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Input Fields Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { if (it.length <= 15) username = it },
                        label = { Text("用户名") },
                        placeholder = { Text("请输入用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            focusedLabelColor = AccentOrange,
                            cursorColor = AccentOrange,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.8f)
                        )
                    )

                    OutlinedTextField(
                        value = signature,
                        onValueChange = { if (it.length <= 50) signature = it },
                        label = { Text("个性签名") },
                        placeholder = { Text("在云朵里养宠物，是生活的小惊喜") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            focusedLabelColor = AccentOrange,
                            cursorColor = AccentOrange,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            // Success feedback message
            AnimatedVisibility(
                visible = showSuccess,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "保存成功！",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Save Button
            Button(
                onClick = {
                    val finalName = username.ifBlank { "Mrh Raju" }
                    val finalSig = signature.ifBlank { "在云朵里养宠物，是生活的小惊喜" }
                    settingsManager.saveUserProfile(
                        UserProfile(finalName, finalSig, selectedAvatarResId)
                    )
                    showSuccess = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(12.dp),
                enabled = username.isNotBlank()
            ) {
                Text(
                    text = "保存偏好",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
