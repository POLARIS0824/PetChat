package com.example.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chat.R
import com.example.chat.model.PetTypes
import com.example.chat.ui.cards.CardsViewModel
import com.example.chat.ui.cards.PetList
import com.example.chat.ui.chat.ChatScreen
import com.example.chat.ui.chat.PetChatViewModel
import com.example.chat.ui.components.PetAvatar
import com.example.chat.ui.navigation.BottomNavItems
import com.example.chat.ui.navigation.DrawerContent
import com.example.chat.ui.navigation.Screen
import com.example.chat.ui.notes.NotesScreen
import com.example.chat.ui.session.SessionListScreen
import com.example.chat.ui.social.SocialScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetChatApp(
    viewModel: PetChatViewModel = hiltViewModel(),
    cardsViewModel: CardsViewModel = hiltViewModel()
) {
    var currentScreen by remember { mutableStateOf(Screen.Chat) }
    var currentPetType by remember { mutableStateOf(PetTypes.CAT) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showPetSelector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAllSessions()
        viewModel.resetScroll()
    }

    MaterialTheme {
        ModalNavigationDrawer(
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
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
                    PetChatTopBar(
                        currentScreen = currentScreen,
                        showPetSelector = showPetSelector,
                        onTogglePetSelector = { showPetSelector = !showPetSelector },
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    )
                },
                bottomBar = {
                    if (currentScreen != Screen.SessionList) {
                        PetChatBottomBar(
                            currentScreen = currentScreen,
                            onScreenSelected = { currentScreen = it }
                        )
                    }
                },
                containerColor = Color(246, 246, 246)
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    PetSelectorOverlay(
                        visible = showPetSelector && currentScreen == Screen.Chat,
                        currentPetType = currentPetType,
                        onSelect = { petType ->
                            currentPetType = petType
                            showPetSelector = false
                        }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .clickable(
                                enabled = showPetSelector,
                                onClick = { showPetSelector = false },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                val direction = if (targetState.ordinal > initialState.ordinal)
                                    AnimatedContentTransitionScope.SlideDirection.Left
                                else
                                    AnimatedContentTransitionScope.SlideDirection.Right

                                val animationSpec = tween<IntOffset>(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                                slideIntoContainer(towards = direction, animationSpec = animationSpec) togetherWith
                                        slideOutOfContainer(towards = direction, animationSpec = animationSpec)
                            },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                Screen.Chat -> ChatScreen(
                                    viewModel = viewModel,
                                    petType = currentPetType,
                                    contentPadding = innerPadding,
                                    showPetSelector = showPetSelector,
                                    onHidePetSelector = { showPetSelector = false }
                                )
                                Screen.Cards -> PetList(
                                    pets = cardsViewModel.pets,
                                    onNavigateToChat = { petType ->
                                        currentPetType = petType
                                        currentScreen = Screen.Chat
                                    }
                                )
                                Screen.Notes -> NotesScreen()
                                Screen.Social -> SocialScreen()
                                Screen.SessionList -> SessionListScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PetChatTopBar(
    currentScreen: Screen,
    showPetSelector: Boolean,
    onTogglePetSelector: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val isPetSelectorActive = showPetSelector && currentScreen == Screen.Chat

    TopAppBar(
        title = {
            when {
                isPetSelectorActive -> Text(
                    "专属萌宠，随时陪伴！",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                currentScreen == Screen.Chat -> Text("")
                currentScreen == Screen.Cards -> {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        Text("名片夹", modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(255, 143, 45))
                    }
                }
                currentScreen == Screen.Notes -> {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        Text("便利贴", modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(255, 143, 45))
                    }
                }
                currentScreen == Screen.Social -> {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        Text("萌友圈", modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(255, 143, 45))
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.padding(start = 8.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.sidebar),
                    contentDescription = "打开抽屉菜单",
                    modifier = Modifier.size(24.dp),
                    tint = if (isPetSelectorActive) Color.White else Color.Unspecified
                )
            }
        },
        actions = {
            if (currentScreen == Screen.Chat) {
                val rotation by animateFloatAsState(
                    targetValue = if (showPetSelector) 180f else 0f,
                    animationSpec = tween(durationMillis = 300)
                )
                IconButton(onClick = onTogglePetSelector, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow),
                        contentDescription = if (showPetSelector) "关闭宠物选择器" else "切换宠物",
                        modifier = Modifier.size(24.dp).rotate(rotation),
                        tint = if (showPetSelector) Color.White else Color.Unspecified
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isPetSelectorActive) Color(255, 178, 110) else Color.White,
            titleContentColor = if (isPetSelectorActive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = if (isPetSelectorActive) Color.White else Color.Unspecified,
            actionIconContentColor = if (isPetSelectorActive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun PetChatBottomBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = Color(255, 253, 246),
        contentColor = Color(250, 142, 57),
        modifier = Modifier.heightIn(min = 72.dp, max = 96.dp)
    ) {
        BottomNavItems.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = if (currentScreen == item.screen) item.selectedIcon else item.unselectedIcon),
                        contentDescription = item.title,
                        tint = if (currentScreen == item.screen) Color(255, 143, 45) else Color.Gray,
                        modifier = Modifier.size(26.dp)
                    )
                },
                label = {
                    Text(
                        item.title,
                        fontSize = 12.sp,
                        fontWeight = if (currentScreen == item.screen) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = currentScreen == item.screen,
                onClick = { onScreenSelected(item.screen) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(255, 143, 45),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(255, 143, 45),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun PetSelectorOverlay(
    visible: Boolean,
    currentPetType: PetTypes,
    onSelect: (PetTypes) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(Color(255, 178, 110))
            .zIndex(2f)
            .offset(y = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 128.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PetAvatar(name = "布丁", imageRes = R.drawable.pet_cat, isSelected = currentPetType == PetTypes.CAT, onClick = { onSelect(PetTypes.CAT) })
                PetAvatar(name = "大白", imageRes = R.drawable.pet_samoyed, isSelected = currentPetType == PetTypes.DOG, onClick = { onSelect(PetTypes.DOG) })
                PetAvatar(name = "豆豆", imageRes = R.drawable.pet_shiba, isSelected = currentPetType == PetTypes.DOG2, onClick = { onSelect(PetTypes.DOG2) })
                PetAvatar(name = "团绒", imageRes = R.drawable.pet_hamster, isSelected = currentPetType == PetTypes.HAMSTER, onClick = { onSelect(PetTypes.HAMSTER) })
            }
        }
    }
}
