package com.example.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chat.model.ChatMessage
import com.example.chat.model.PetTypes
import kotlinx.coroutines.launch
import java.util.*
import com.example.chat.ui.NotesScreen
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.chat.model.ChatSession
import java.text.SimpleDateFormat
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.contentValuesOf
import com.example.chat.ui.social.SocialScreen
import com.example.chat.viewmodel.CardsViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // 处理权限结果
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置沉浸式状态栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // 检查并请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        lifecycleScope.launch {
            // 可以在这里安全地调用挂起函数
        }

        setContent {
            MaterialTheme {
                PetChatApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 保存当前状态
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
fun PetChatApp(
    viewModel: PetChatViewModel = viewModel(),
    cardsViewModel: CardsViewModel = viewModel() // 添加CardsViewModel
) {
    var currentScreen by remember { mutableStateOf(Screen.Chat) }
    var currentPetType by remember { mutableStateOf(PetTypes.CAT) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    MaterialTheme {
        ModalNavigationDrawer(
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        currentPetType = currentPetType,
                        onPetTypeSelected = {
                            currentPetType = it
                            scope.launch { drawerState.close() }
                        },
                        onClose = {
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            },
            drawerState = drawerState,
            gesturesEnabled = true,
            scrimColor = Color.Black.copy(alpha = 0.32f) // Material 3 的标准值
        ) {
            Scaffold(
                modifier = Modifier
                    .systemBarsPadding()
                    .background(Color(246,246,246)),
                containerColor = Color(246,246,246),
                topBar = {
                    when (currentScreen) {
                        Screen.Chat -> {
                            TopAppBar(
                                title = { Text("") },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.sidebar),
                                            contentDescription = "打开抽屉菜单",
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.Unspecified
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { /* 切换宠物 */ },
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.arrow),
                                            contentDescription = "切换宠物",
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.Unspecified
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.White,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    navigationIconContentColor = Color.Unspecified,
                                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
//                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        Screen.Cards -> {
                            TopAppBar(
                                title = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            "名片夹",
                                            modifier = Modifier.padding(end = 8.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(255,143, 45)
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.sidebar),
                                            contentDescription = "打开抽屉菜单",
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.Unspecified
                                        )
                                    }
                                },
                                actions = { /* 其他操作 */ },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.White,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        Screen.Notes -> {
                            TopAppBar(
                                title = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            "便利贴",
                                            modifier = Modifier.padding(end = 8.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(255,143, 45)
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.sidebar),
                                            contentDescription = "打开抽屉菜单",
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.Unspecified
                                        )                                    }
                                },
                                actions = { /* 其他操作 */ },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.White,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        Screen.Social -> {
                            TopAppBar(
                                title = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            "萌友圈",
                                            modifier = Modifier.padding(end = 8.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(255,143, 45)
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.sidebar),
                                            contentDescription = "打开抽屉菜单",
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.Unspecified
                                        )                                    }
                                },
                                actions = { /* 其他操作 */ },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.White,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.White,
                        contentColor = Color(250,142, 57),
                    ) {
                        BottomNavItems.forEach { item ->
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (currentScreen == item.screen)
                                                item.selectedIcon
                                            else
                                                item.unselectedIcon
                                        ),
                                        contentDescription = item.title,
                                        tint = if(currentScreen == item.screen)
                                            Color(255,143, 45)
                                        else
                                            Color.Gray
                                    )
                                },
                                label = { Text(item.title) },
                                selected = currentScreen == item.screen,
                                onClick = { currentScreen = item.screen },
                                // 设置选中和未选中的颜色
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Transparent,
                                    unselectedIconColor = Color(237,133, 42),
                                    selectedTextColor = Color(237,133, 42),
                                    unselectedTextColor = Color(237,133, 42),
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            ) { contentPadding ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .imePadding()

//                    .imeNestedScroll()
                ) {
                    when (currentScreen) {
                        Screen.Chat -> {
                            ChatScreen(
                                viewModel = viewModel,
                                petType = currentPetType,
                                onDrawerClick = {
                                    scope.launch { drawerState.open() }
                                },
                                contentPadding = contentPadding
                            )
                        }
                        Screen.Cards -> {
                            PetList(cardsViewModel.pets)
                        }
                        Screen.Notes -> NotesScreen()
                        Screen.Social -> SocialScreen()
                    }
                }
            }
        }
    }
}

// 底部导航项
private val BottomNavItems = listOf(
    NavItem(
        Screen.Chat,
        "聊天",
        R.drawable.chat_outline,
        R.drawable.chat_fill),
    NavItem(
        Screen.Cards,
        "名片夹",
        R.drawable.par_outline,
        R.drawable.par_fill),
    NavItem(
        Screen.Notes,
        "便利贴",
        R.drawable.bag_outline,
        R.drawable.bag_fill),
    NavItem(
        Screen.Social,
        "萌友圈",
        R.drawable.adopt_outline,
        R.drawable.adopt_fill)
)

// 导航项数据类
private data class NavItem(
    val screen: Screen,
    val title: String,
    val unselectedIcon: Int,
    val selectedIcon: Int
)

// 屏幕枚举
private enum class Screen {
    Chat, Cards, Notes, Social
}

@Composable
fun ChatSessionItem(
    session: ChatSession,
    onClick: (ChatSession) -> Unit
) {
    Surface(
        onClick = { onClick(session) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Image(
                painter = painterResource(id = session.avatarRes),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 会话信息
            Column {
                Text(
                    text = session.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                if (session.lastMessage.isNotEmpty()) {
                    Text(
                        text = session.lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatSessionItemPreview() {
    MaterialTheme {
        ChatSessionItem(
            session = ChatSession(
                id = "cat",
                petType = PetTypes.CAT, // Assuming you have the PetTypes enum from the previous example
                displayName = "Cat Session",
                avatarRes = R.drawable.ic_cat_avatar, // Replace with your actual drawable
                lastMessage = "This is a preview message."
            ),
            onClick = { session -> println("Clicked on ${session.displayName}") }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: PetChatViewModel,
    petType: PetTypes,
    onDrawerClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var showSettings by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isForegroundLoading = viewModel.isForegroundLoading

    val frames = listOf(
        R.drawable.frame1,
        R.drawable.frame2,
        R.drawable.frame3,
        R.drawable.frame4,
        R.drawable.frame5,
        R.drawable.frame6,
        R.drawable.frame7,
        R.drawable.frame8,
        R.drawable.frame9,
        R.drawable.frame10
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedAvatar(
                frameResIds = frames,
                modifier = Modifier
                    .padding(start = 24.dp, top = 24.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .zIndex(1f)
            )

            // 聊天消息列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(
                    items = viewModel.getChatHistory(petType),
                    key = { it.hashCode() }
                ) { message ->
                    ChatBubble(
                        message = message,
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }

            // 直接在这里添加ChatInput，不使用Scaffold的bottomBar
            ChatInput(
                message = message,
                onMessageChange = { message = it },
                // 点击发送消息
                // ChatInput.onSendClick() → PetChatViewModel.sendMessage() → PetChatRepository.getPetResponseWithPictureInfo() →
                // PetChatRepository.getPetResponse() → PetChatRepository.makeApiRequest()
                onSendClick = {
                    if (message.isNotEmpty()) {
                        viewModel.sendMessage(message)
                        message = ""
                        coroutineScope.launch {
                            listState.animateScrollToItem(viewModel.getChatHistory(petType).size - 1)
                        }
                    }
                },
                isLoading = viewModel.isForegroundLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .consumeWindowInsets(contentPadding)
                    .imePadding()
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun AnimatedAvatar(
    frameResIds: List<Int>,
    modifier: Modifier = Modifier,
    frameDelay: Long = 150L
) {
    var currentFrame by remember { mutableStateOf(0) }

// 添加过渡动画
    val transition = updateTransition(
        targetState = currentFrame,
        label = "Avatar Animation"
    )

    val alpha by transition.animateFloat(
        label = "Alpha",
        transitionSpec = { tween(frameDelay.toInt() / 2) }
    ) { frame ->
        1f
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(frameDelay)
            currentFrame = (currentFrame + 1) % frameResIds.size
        }
    }

    Image(
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
    modifier: Modifier = Modifier
) {
    val isFromUser = message.isFromUser
    val backgroundColor = if (isFromUser)
        Color(255,143, 45)
    else
        Color(255, 255, 255)

    val textColor = if (isFromUser)
        Color.White
    else
        Color.Black

    val arrangement = if (isFromUser)
        Arrangement.End else Arrangement.Start

    val bubbleShape = if (isFromUser)
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    else
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

    val timeString = SimpleDateFormat("HH:mm", Locale.getDefault())
        .format(System.currentTimeMillis())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = arrangement
    ) {
        Column(
            horizontalAlignment = if (isFromUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = bubbleShape,
                color = backgroundColor,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text(
                        text = message.content,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
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
fun LoadingAnimation(
    modifier: Modifier = Modifier,
    dotSize: Float = 36f,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    animationDuration: Int = 1000, // Total duration for one up-and-down cycle
    delayBetweenDots: Int = 200    // Delay between the start of each dot's animation
) {
    val maxOffset = 8f // Maximum vertical offset for the animation

    // Remember the animation state for each dot
    val infiniteTransitions = (0 until 4).map { rememberInfiniteTransition(label = "") }
    val offsets = infiniteTransitions.mapIndexed { index, it ->
        it.animateFloat(
            initialValue = 0f,
            targetValue = -maxOffset,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = animationDuration
                    0f at 0 // Start at 0 offset
                    maxOffset at animationDuration / 4 // Max offset at 1/4 of the duration
                    maxOffset at animationDuration * 3 / 4 // Stay at max offset until 3/4
                    0f at animationDuration // Back to 0 at the end
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(delayBetweenDots * index) // Staggered start
            ), label = ""
        )
    }

    // Use a Canvas to draw the dots
    Canvas(modifier = modifier) {
        val center = size.width / 2
        val dotSpacing = dotSize * 1.5f // Space between dots

        // Calculate the starting x-position to center the group of dots
        val startX = center - (dotSpacing * 1.5f)

        // Draw each dot
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
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (isLoading) {
//            LoadingAnimation(
//                modifier = Modifier
//                    .padding(horizontal = 16.dp, vertical = 8.dp)
//            )
//            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                LoadingAnimation(
                    modifier = Modifier
                        .height(36.dp)
                        .width(128.dp)
//                        .size(128.dp) // 设置合理的大小，避免超出屏幕
                        .align(Alignment.CenterVertically) // 垂直居中对齐
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color.White)
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "+" Icon Button
            IconButton(
                onClick = { /* TODO: Handle "+" button click */ },
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more), // Replace with your "+" icon
                    contentDescription = "Add",
                    tint = Color.Gray // Adjust the color as needed
                )
            }

            // Text Field with Chinese text
            TextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                placeholder = {
                    Text(
                        "Message...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        onSendClick()
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            // Send Icon Button
            IconButton(
                onClick = onSendClick,
                modifier = Modifier.padding(start = 4.dp),
                enabled = message.isNotEmpty()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send), // Replace with your send icon
                    contentDescription = "Send",
                    tint = Color(0xFFE9673A) // Adjust the color as needed
                )
            }
        }
    }
}

@Preview
@Composable
fun ChatInputPreview() {
    MaterialTheme { // Provide a MaterialTheme for the preview
        ChatInput(
            message = "Hello",
            onMessageChange = { /* Handle message change (not used in preview) */ },
            onSendClick = { /* Handle send click (not used in preview) */ },
            isLoading = false
        )
    }
}

@Preview
@Composable
fun ChatInputLoadingPreview() {
    MaterialTheme {
        ChatInput(
            message = "",
            onMessageChange = { /* Handle message change (not used in preview) */ },
            onSendClick = { /* Handle send click (not used in preview) */ },
            isLoading = true
        )
    }
}

@Composable
fun PetList(pets: List<com.example.chat.model.Pet>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(255, 255, 255)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(pets) { pet ->
            PetCard(pet)
        }
    }
}

@Composable
fun PetCard(pet: com.example.chat.model.Pet) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 左上角的橙色圆点装饰
            Canvas(
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.TopStart)
            ) {
                drawCircle(
                    color = Color(255, 143, 45, 40),
                    radius = 80.dp.toPx(),
                    center = Offset(0f, 0f)
                )
            }

            // 右下角的橙色圆点装饰
            Canvas(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.BottomEnd)
            ) {
                drawCircle(
                    color = Color(255, 143, 45, 30),
                    radius = 180.dp.toPx(),
                    center = Offset(size.width, size.height)
                )
            }

            // 背景图片
            Image(
                painter = painterResource(id = pet.imageRes),
                contentDescription = "Pet Image",
                modifier = Modifier.size(300.dp),
                contentScale = ContentScale.Crop
            )

            // 毛玻璃效果底层
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(100.dp) // 固定高度
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.5f)
                            ),
                            startY = 0f,
                            endY = 50f
                        )
                    )
                    .blur(radius = 20.dp) // 增加模糊半径
            )

            // 信息内容层(在毛玻璃效果之上)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 名字和品种行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pet.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black // 确保文字在毛玻璃背景上清晰可见
                    )
                    Text(
                        text = pet.breed,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 其他信息...
            }
        }
    }
}

@Composable
private fun DrawerContent(
    currentPetType: PetTypes,
    onPetTypeSelected: (PetTypes) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
    ) {
        // 用户信息区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
//                // 用户头像
//                Image(
//                    painter = painterResource(id = R.drawable.avatar_placeholder), // 替换成你的默认头像
//                    contentDescription = null,
//                    modifier = Modifier
//                        .size(60.dp)
//                        .clip(CircleShape)
//                )

                Spacer(modifier = Modifier.width(12.dp))

                // 用户名和认证标识
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mrh Raju",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(4.dp))
//                        Icon(
//                            painter = painterResource(id = R.drawable.ic_verified), // 替换成你的认证图标
//                            contentDescription = "已认证",
//                            tint = Color(0xFF00C853),
//                            modifier = Modifier.size(16.dp)
//                        )
                    }
                    Text(
                        text = "Verified Profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }

//        // 深色模式开关
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(vertical = 16.dp),
//            horizontalArrangement = Arrangement.SpaceBetween,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Row(
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Icon(
//                    painter = painterResource(id = R.drawable.ic_dark_mode), // 替换成你的深色模式图标
//                    contentDescription = "深色模式",
//                    modifier = Modifier.size(24.dp)
//                )
//                Spacer(modifier = Modifier.width(12.dp))
//                Text(
//                    text = "深色模式",
//                    style = MaterialTheme.typography.bodyLarge
//                )
//            }
//            Switch(
//                checked = false, // 这里需要绑定实际的深色模式状态
//                onCheckedChange = { /* 处理深色模式切换 */ }
//            )
//        }

        // 设置选项列表
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            DrawerMenuItem(
                icon = R.drawable.ic_account,
                text = "账号信息"
            )
            DrawerMenuItem(
                icon = R.drawable.ic_password,
                text = "密码设置"
            )
            DrawerMenuItem(
                icon = R.drawable.ic_favorite,
                text = "偏好设置"
            )
            DrawerMenuItem(
                icon = R.drawable.ic_settings,
                text = "系统设置"
            )
        }

        // 退出登录按钮
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            onClick = { /* 处理退出登录 */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_logout), // 替换成你的退出图标
                    contentDescription = "退出登录",
                    tint = Color(0xFFFF5252)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "退出登录",
                    color = Color(0xFFFF5252),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: Int,
    text: String,
    onClick: () -> Unit = {}
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = text,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                // 添加设置选项
                Text("设置选项将在这里显示")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}
