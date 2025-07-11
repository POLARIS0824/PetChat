package com.example.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RenderEffect
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
import java.util.Calendar
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
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
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key.Companion.Window
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.contentValuesOf
import androidx.core.view.ViewCompat
import com.example.chat.model.Pet
import com.example.chat.ui.social.SocialScreen
import com.example.chat.viewmodel.CardsViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // 处理权限结果
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val decorView = window.decorView
        val wic = ViewCompat.getWindowInsetsController(decorView)

        wic?.let {
            val isLightBackground = true
            it.isAppearanceLightStatusBars = isLightBackground
        }


        // 设置沉浸式状态栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

//        // 检查并请求通知权限
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (ContextCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.POST_NOTIFICATIONS
//                ) != PackageManager.PERMISSION_GRANTED) {
//                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
//            }
//        }

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

//@OptIn(
//    ExperimentalMaterial3Api::class
//)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetChatApp(
    viewModel: PetChatViewModel = viewModel(),
    cardsViewModel: CardsViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf(Screen.Chat) }
    var currentPetType by remember { mutableStateOf(PetTypes.CAT) }
    var previousScreen by remember { mutableStateOf(Screen.Chat) } // 添加记录前一个屏幕的状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showPetSelector by remember { mutableStateOf(false) }
    
    // 初始化加载所有会话和聊天历史
    LaunchedEffect(Unit) {
        viewModel.loadAllSessions()
        // 确保首次打开应用时也能滚动到最新消息
        viewModel.resetScroll()
    }

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
                        },
                        onNavigateToSessionList = {
                            currentScreen = Screen.SessionList
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            },
            drawerState = drawerState,
            gesturesEnabled = true,
            scrimColor = Color.Black.copy(alpha = 0.32f)
        ) {
            Scaffold(
                topBar = {
                    // 统一的 TopAppBar
                    TopAppBar(
                        title = {
                            when {
                                showPetSelector && currentScreen == Screen.Chat -> {
                                    Text(
                                        "专属萌宠，随时陪伴！",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                currentScreen == Screen.Chat -> Text("")
                                currentScreen == Screen.Cards -> {
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
                                            color = Color(255, 143, 45)
                                        )
                                    }
                                }
                                currentScreen == Screen.Notes -> {
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
                                            color = Color(255, 143, 45)
                                        )
                                    }
                                }
                                currentScreen == Screen.Social -> {
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
                                            color = Color(255, 143, 45)
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.open() }
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.sidebar),
                                    contentDescription = "打开抽屉菜单",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (showPetSelector && currentScreen == Screen.Chat)
                                        Color.White
                                    else
                                        Color.Unspecified
                                )
                            }
                        },
                        actions = {
                            if (currentScreen == Screen.Chat) {
                                // 添加旋转动画
                                val rotation by animateFloatAsState(
                                    targetValue = if (showPetSelector) 180f else 0f,
                                    animationSpec = tween(durationMillis = 300)
                                )

                                IconButton(
                                    onClick = {
                                        showPetSelector = !showPetSelector
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.arrow),
                                        contentDescription = if (showPetSelector) "关闭宠物选择器" else "切换宠物",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .rotate(rotation), // 使用动画旋转值
                                        tint = if (showPetSelector) Color.White else Color.Unspecified
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (showPetSelector && currentScreen == Screen.Chat)
                                Color(255,178,110)
                            else
                                Color.White,
                            titleContentColor = if (showPetSelector && currentScreen == Screen.Chat)
                                Color.White
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = if (showPetSelector && currentScreen == Screen.Chat)
                                Color.White
                            else
                                Color.Unspecified,
                            actionIconContentColor = if (showPetSelector && currentScreen == Screen.Chat)
                                Color.White
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color(255,253,246),
                        contentColor = Color(250,142, 57),
                        modifier = Modifier
                            .heightIn(min = 72.dp, max = 96.dp)
                            .padding(vertical = 0.dp),
//                        windowInsets = WindowInsets(0, 0, 0, 0) // 移除系统默认的内边距
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
                                            Color(255, 143, 45)
                                        else
                                            Color.Gray,
                                        modifier = Modifier
                                            .size(26.dp) // 调整图标大小
                                    )
                                },
                                label = {
                                    Text(
                                        item.title,
                                        fontSize = 12.sp, // 调整文字大小
                                        fontWeight = if(currentScreen == item.screen)
                                            FontWeight.Bold
                                        else
                                            FontWeight.Normal
                                    )
                                },
                                selected = currentScreen == item.screen,
                                onClick = { currentScreen = item.screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(255, 143, 45),
                                    unselectedIconColor = Color.Gray,
                                    selectedTextColor = Color(255, 143, 45),
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = Color.Transparent // 移除指示器背景
                                )
                            )
                        }
                    }
                },
                containerColor = Color(246,246,246)
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // 宠物选择器覆盖层
                    AnimatedVisibility(
                        visible = showPetSelector && currentScreen == Screen.Chat,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart= 20.dp, bottomEnd = 20.dp))
                            .background(Color(255, 178, 110))
                            .zIndex(2f) // 提高z-index确保在最上层
                            .offset(y = 0.dp) // 确保从顶部开始
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 128.dp, start = 16.dp, end = 16.dp, bottom = 16.dp) // 调整顶部padding
                        ) {
                            // 宠物头像行
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 布丁 (猫咪)
                                PetAvatar(
                                    name = "布丁",
                                    imageRes = R.drawable.pet_cat,
                                    isSelected = currentPetType == PetTypes.CAT,
                                    onClick = {
                                        currentPetType = PetTypes.CAT
                                        showPetSelector = false
                                    }
                                )

                                // 大白 (萨摩耶)
                                PetAvatar(
                                    name = "大白",
                                    imageRes = R.drawable.pet_samoyed,
                                    isSelected = currentPetType == PetTypes.DOG,
                                    onClick = {
                                        currentPetType = PetTypes.DOG
                                        showPetSelector = false
                                    }
                                )

                                // 豆豆 (柴犬)
                                PetAvatar(
                                    name = "豆豆",
                                    imageRes = R.drawable.pet_shiba,
                                    isSelected = currentPetType == PetTypes.DOG2,
                                    onClick = {
                                        currentPetType = PetTypes.DOG2
                                        showPetSelector = false
                                    }
                                )

                                // 团绒 (仓鼠)
                                PetAvatar(
                                    name = "团绒",
                                    imageRes = R.drawable.pet_hamster,
                                    isSelected = currentPetType == PetTypes.HAMSTER,
                                    onClick = {
                                        currentPetType = PetTypes.HAMSTER
                                        showPetSelector = false
                                    }
                                )
                            }
                        }
                    }

                    // 主内容区域
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .clickable(
                                enabled = showPetSelector,
                                onClick = { showPetSelector = false },
                                indication = null,
                                interactionSource = remember {
                                    MutableInteractionSource()
                                }
                            )
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                // 判断滑动方向
                                val direction = if (targetState.ordinal > initialState.ordinal) {
                                    // 向左滑动（新屏幕从右边进入）
                                    AnimatedContentTransitionScope.SlideDirection.Left
                                } else {
                                    // 向右滑动（新屏幕从左边进入）
                                    AnimatedContentTransitionScope.SlideDirection.Right
                                }

                                // 创建滑动动画，使用FastOutSlowInEasing实现fastThenSlow效果
                                val animationSpec = tween<IntOffset>(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                                slideIntoContainer(
                                    towards = direction,
                                    animationSpec = animationSpec
                                ) togetherWith slideOutOfContainer(
                                    towards = direction,
                                    animationSpec = animationSpec
                                )
                            },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                Screen.Chat -> ChatScreen(
                                    viewModel = viewModel,
                                    petType = currentPetType,
                                    onDrawerClick = { scope.launch { drawerState.open() } },
                                    contentPadding = innerPadding,
                                    showPetSelector = showPetSelector,
                                    onHidePetSelector = { showPetSelector = false }
                                )
                                Screen.Cards -> {
                                    PetList(
                                        pets = cardsViewModel.pets,
                                        onNavigateToChat = { petType ->
                                            previousScreen = currentScreen
                                            currentPetType = petType
                                            currentScreen = Screen.Chat
                                        }
                                    )
                                }
                                Screen.Notes -> {
                                    NotesScreen()
                                }
                                Screen.Social -> {
                                    SocialScreen()
                                }
                                Screen.SessionList -> {
                                    SessionListScreen(
                                        viewModel = viewModel,
                                        onSessionSelected = { sessionId ->
                                            viewModel.switchToSession(sessionId)
                                            currentScreen = Screen.Chat
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 宠物头像组件
@Composable
fun PetAvatar(
    name: String,
    imageRes: Int,
    onClick: () -> Unit,
    isSelected: Boolean = false  // 添加选中状态参数
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        // 圆形头像
        Box {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = name,
                modifier = Modifier
                    .size(LocalConfiguration.current.screenWidthDp.dp * 0.2f)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )

            // 如果被选中，显示右下角的圆点指示器
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-1).dp, y = (-1).dp)  // 稍微向内偏移
                        .background(Color(255,143, 45), CircleShape)  // 橙色圆点
                        .border(1.dp, Color.White, CircleShape)  // 白色边框
                )
            }
        }
        // 宠物名称
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) Color.Yellow else Color.White,  // 选中时文字变黄
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,  // 选中时文字加粗
            modifier = Modifier.padding(top = 4.dp)
        )
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
    Chat, Cards, Notes, Social, SessionList
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: PetChatViewModel,
    petType: PetTypes,
    onDrawerClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showPetSelector: Boolean = false, // 添加宠物选择器状态参数
    onHidePetSelector: () -> Unit = {} // 添加关闭宠物选择器的回调
) {
    var showSettings by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 每次进入聊天页面时自动滚动到最新消息
    LaunchedEffect(petType) {
        if (viewModel.chatHistory.isNotEmpty()) {
            delay(300) // 等待布局完成
            listState.animateScrollToItem(viewModel.chatHistory.size - 1)
        }
    }

    val isForegroundLoading = viewModel.isForegroundLoading
    val isStreaming = viewModel.isStreaming
    val streamingMessage = viewModel.streamingMessage

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

    val emptyStateImages = listOf(
        R.drawable.greeting,
        R.drawable.greeting2,
        R.drawable.greeting3,
    )

    // 使用remember随机选择一张图片
    val randomImageRes = remember {
        emptyStateImages.random()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
//            AnimatedAvatar(
//                frameResIds = frames,
//                modifier = Modifier
//                    .padding(start = 24.dp, top = 24.dp)
//                    .size(48.dp)
//                    .clip(CircleShape)
//                    .zIndex(1f)
//            )

            LaunchedEffect(petType) {
                viewModel.selectPetType(petType)
            }

            // 检查聊天历史是否为空
            if (viewModel.chatHistory.isEmpty()) {
                // 显示空状态图片
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            enabled = showPetSelector,
                            onClick = onHidePetSelector,
                            indication = null,
                            interactionSource = remember {
                                MutableInteractionSource()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = randomImageRes), // 使用随机选择的图片
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
                // 聊天消息列表
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            enabled = showPetSelector,
                            onClick = onHidePetSelector,
                            indication = null,
                            interactionSource = remember {
                                MutableInteractionSource()
                            }
                        ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(
                        items = viewModel.chatHistory,
                        key = { it.hashCode() }
                    ) { message ->
                        // 检查这条消息是否是正在流式传输的消息
                        val isCurrentlyStreaming = isStreaming && 
                                streamingMessage != null && 
                                !message.isFromUser && 
                                message == viewModel.chatHistory.lastOrNull { !it.isFromUser }
                        
                        ChatBubble(
                            message = message,
                            modifier = Modifier.animateItemPlacement(),
                            isStreaming = isCurrentlyStreaming
                        )
                    }
                }
                
                // 处理滚动到底部
                LaunchedEffect(viewModel.shouldScrollToBottom, viewModel.chatHistory.size) {
                    if ((viewModel.shouldScrollToBottom || viewModel.chatHistory.isNotEmpty()) && viewModel.chatHistory.isNotEmpty()) {
                        // 先滚动到最后一个项目
                        listState.animateScrollToItem(viewModel.chatHistory.size - 1)
                        
                        // 计算额外滚动距离 - 根据最后一条消息的长度动态调整
                        val lastMessage = viewModel.chatHistory.last()
                        val extraScrollDistance = if (!lastMessage.isFromUser) {
                            // 非用户消息可能较长，需要更多滚动
                            val contentLength = lastMessage.content.length
                            // 根据内容长度计算滚动距离，最小200f，最大500f
                            (200f + contentLength * 0.5f).coerceAtMost(500f)
                        } else {
                            // 用户消息通常较短
                            100f
                        }
                        
                        // 然后使用额外的滚动来确保显示完整的消息
                        delay(150) // 延长延迟以确保布局完全完成
                        listState.scrollBy(extraScrollDistance) // 使用动态计算的滚动距离
                        
                        // 如果是流式传输中，添加额外的滚动逻辑
                        if (viewModel.isStreaming && !lastMessage.isFromUser) {
                            delay(100) // 再次延迟
                            // 再次滚动以确保显示最新内容
                            listState.scrollToItem(viewModel.chatHistory.size - 1)
                        }
                    }
                }
                
                // 监听流式传输状态，确保在流式传输过程中持续滚动到底部
                LaunchedEffect(viewModel.streamingMessage) {
                    viewModel.streamingMessage?.let { message ->
                        if (!message.isFromUser && message.content.isNotEmpty()) {
                            delay(100)
                            // 确保滚动到最新位置
                            listState.animateScrollToItem(viewModel.chatHistory.size - 1)
                        }
                    }
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
                isStreaming = isStreaming, // 传入流式传输状态
                onFocusChanged = { isFocused ->
                    // 当输入框获得焦点时触发滚动到底部
                    if (isFocused && viewModel.chatHistory.isNotEmpty()) {
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
                        // 点击输入框时触发滚动到底部
                        viewModel.resetScroll()
                    }
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
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.content,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        // 如果是流式传输中的消息，显示打字指示器
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
        // 创建三个点的动画
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
    dotColor: Color = Color(255, 143, 45),
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
    modifier: Modifier = Modifier,
    showPetSelector: Boolean = false, // 添加宠物选择器状态参数
    onHidePetSelector: () -> Unit = {}, // 添加关闭宠物选择器的回调
    isStreaming: Boolean = false, // 添加流式传输状态参数
    onFocusChanged: (Boolean) -> Unit = {} // 添加焦点变化回调
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(
                enabled = showPetSelector,
                onClick = onHidePetSelector,
                indication = null,
                interactionSource = remember {
                    MutableInteractionSource()
                }
            ),
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
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                LoadingAnimation(
                    modifier = Modifier
                        .height(30.dp)
                        .fillMaxWidth(0.3f)
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
                .padding(horizontal = 12.dp, vertical = 2.dp),
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
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .clickable(
                        enabled = showPetSelector,
                        onClick = {
                            onHidePetSelector()
                        },
                        indication = null,
                        interactionSource = remember {
                            MutableInteractionSource()
                        }
                    )
                    .onFocusChanged { focusState ->
                        // 当输入框获得焦点时触发回调
                        onFocusChanged(focusState.isFocused)
                    },
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
                    focusedContainerColor = Color(247,247,252),
                    unfocusedContainerColor = Color(247,247,252),
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
                enabled = message.isNotEmpty() && !isStreaming // 在流式传输过程中禁用发送按钮
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send), // Replace with your send icon
                    contentDescription = "Send",
                    tint = Color(255,143, 45) // Adjust the color as needed
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
            isLoading = false,
            onFocusChanged = { /* Handle focus change (not used in preview) */ }
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
            isLoading = true,
            onFocusChanged = { /* Handle focus change (not used in preview) */ }
        )
    }
}

@Composable
fun PetList(pets: List<com.example.chat.model.Pet>,
            modifier: Modifier = Modifier,
            onNavigateToChat: (PetTypes) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(255, 255, 255)),
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
    pet: com.example.chat.model.Pet,
    onChatClick: (PetTypes) -> Unit = {}
) {
    // 定义最大拖拽距离
    val maxDragDistance = 200.dp
    // 使用LocalDensity获取density转换器
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { maxDragDistance.toPx() }

    // 计算初始偏移量
    val initialVisiblePercentage = 0f

    // 记录当前可见百分比
    var visiblePercentage by remember { mutableFloatStateOf(initialVisiblePercentage) }

    // 获取卡片尺寸
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

//    // 计算卡片高度
//    val cardHeight = with(density) { 320.dp.toPx() }

    // 计算从卡片底部四分之一处开始的偏移量
    val startOffsetY = cardSize.height * 0.67f // 底部四分之一处
    val maxOffsetY = cardSize.height * 0.5f // 最大偏移为卡片高度的一半

    // 修改偏移量计算方式
    val offsetY = startOffsetY - (startOffsetY * visiblePercentage).coerceIn(0f, maxOffsetY)

    // 计算模糊半径 - 根据可见百分比动态变化
    val blurRadius = (50f * visiblePercentage).coerceAtLeast(0.01f)


    // 初始化
    LaunchedEffect(Unit) {
        visiblePercentage = initialVisiblePercentage
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)  // 设置宽高比
            .onSizeChanged { cardSize = it },  // 获取卡片实际尺寸
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // 拖拽结束时，根据可见百分比决定是否回弹
                            visiblePercentage = if (visiblePercentage < 0.5f) {
                                0f // 回弹到初始位置
                            } else {
                                1f // 保持完全展开
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // 计算新的可见百分比，并限制在0-1之间
                            val dragDelta = -dragAmount.y / maxOffsetPx // 向上拖动为正
                            visiblePercentage = (visiblePercentage + dragDelta).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            // 底层图片 - 宠物图片（应用模糊效果）
            Image(
                painter = painterResource(id = pet.initalRes),
                contentDescription = "Pet Image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 根据可见百分比应用模糊效果
                        renderEffect = RenderEffect
                            .createBlurEffect(
                                blurRadius, blurRadius,
                                android.graphics.Shader.TileMode.DECAL
                            )
                            .asComposeRenderEffect()
                    },
                contentScale = ContentScale.Crop
            )

            // 上层图片 - 使用blur.png
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .offset { IntOffset(0, offsetY.roundToInt()) }
            ) {
                // 使用blur.png作为上层图片
                Image(
                    painter = painterResource(id = R.drawable.blur),
                    contentDescription = "Blur Overlay",
                    modifier = Modifier
                        .fillMaxWidth() // 确保宽度填满父容器
                        .wrapContentHeight() // 高度根据比例自适应
                        .align(Alignment.TopCenter), // 顶部对齐
//                    contentScale = ContentScale.FillWidth // 填充宽度，保持宽高比
                )

                // 宠物信息内容
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.06f),
                            end = min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.06f),
                            top = min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.06f),
                            bottom = min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.06f)
                        )
//                        .verticalScroll(rememberScrollState())
                ) {
                    // 宠物名称和状态
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
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp) // 添加左右padding并保留顶部padding
                    )

                    Spacer(modifier = Modifier.height(min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.05f)))

                    // 宠物信息标签
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
//                        verticalAlignment = Alignment.CenterVertically
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
//
//                    Spacer(modifier = Modifier.height(min(12.dp, LocalConfiguration.current.screenWidthDp.dp * 0.04f)))
//
//                    // 活动和性格特点
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.Center,
////                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        InfoTag(
//                            text = "爱好:${pet.hobby}",
//                            backgroundColor = Color(0xFFBDE4F0),
//                            Modifier.padding(end = min(12.dp, LocalConfiguration.current.screenWidthDp.dp * 0.03f)) // 减小间距
//                        )
//
//                        InfoTag(
//                            text = pet.character,
//                            backgroundColor = Color(0xFFD0BDF0),
//                        )
//                    }

                    Spacer(modifier = Modifier.height(min(24.dp, LocalConfiguration.current.screenWidthDp.dp * 0.04f))) // 减小间距

                    // 删除和去对话按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 删除按钮
                        Button(
                            onClick = { /* Handle delete click */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(end = 24.dp)
                                .weight(1f)
                                .width(160.dp)
                        ) {
                            Text("删除", color = Color.Black)
                        }

                        // 去对话按钮
                        Button(
                            onClick = {
                                onChatClick(pet.petType)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(255,166, 88)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .width(160.dp)
                                .weight(1f)
//                            modifier = Modifier.weight(1f)
                        ) {
                            Text("去对话", color = Color.White)
                        }
                    }

                }

                // 右上角表情图标
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 32.dp,
                            end = 16.dp)
                ) {
                    // 使用Box组合布局来叠加背景和图标
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        // 背景图片
                        Image(
                            painter = painterResource(id = R.drawable.background_icon), // 替换为你的背景图片资源
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            contentScale = ContentScale.Crop
                        )

                        // 前景图标
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

// 信息标签组件
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
//            fontSize = min(14.sp, LocalConfiguration.current.screenWidthDp.sp * 0.035f),  // 根据屏幕宽度调整字体大小
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DrawerContent(
    currentPetType: PetTypes,
    onPetTypeSelected: (PetTypes) -> Unit,
    onClose: () -> Unit,
    onNavigateToSessionList: () -> Unit = {}
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
                            text = "POLARIS",
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
                        text = "Unique Studio",
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
                text = "会话列表",
                onClick = onNavigateToSessionList
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(viewModel: PetChatViewModel, onSessionSelected: (String) -> Unit) {
    val sessions by viewModel.allSessions.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏
        TopAppBar(
            title = { Text("我的宠物聊天") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        
        // 会话列表说明
        Text(
            text = "选择一个宠物开始聊天",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
        
        // 会话列表
        LazyColumn {
            items(sessions) { session ->
                SessionItem(
                    session = session,
                    onClick = { onSessionSelected(session.sessionId) }
                )
            }
        }
    }
}

// 根据宠物类型获取头像资源ID
fun getPetAvatar(petType: PetTypes): Int {
    return when (petType) {
        PetTypes.CAT -> R.drawable.pet_cat
        PetTypes.DOG -> R.drawable.pet_shiba
        PetTypes.HAMSTER -> R.drawable.pet_hamster
        PetTypes.DOG2 -> R.drawable.pet_samoyed
    }
}

// 格式化时间戳为可读时间
fun formatTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val messageTime = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        now.get(Calendar.YEAR) != messageTime.get(Calendar.YEAR) -> {
            SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(timestamp)
        }
        now.get(Calendar.DAY_OF_YEAR) != messageTime.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("MM月dd日", Locale.getDefault()).format(timestamp)
        }
        else -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp)
        }
    }
}

@Composable
fun SessionItem(session: PetChatViewModel.SessionInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 宠物头像
        Image(
            painter = painterResource(id = getPetAvatar(session.petType)),
            contentDescription = "宠物头像",
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = session.petName,
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = session.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = formatTime(session.timestamp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}