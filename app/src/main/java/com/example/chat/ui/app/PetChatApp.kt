package com.example.chat.ui.app

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import com.example.chat.ui.theme.AccentOrange
import com.example.chat.ui.theme.BottomBarBackground
import com.example.chat.ui.theme.BottomBarContent
import com.example.chat.ui.theme.PetSelectorBackground
import com.example.chat.ui.theme.ScaffoldBackground
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.chat.R
import com.example.chat.model.PetType
import com.example.chat.data.repository.SettingsManager
import com.example.chat.service.PetGreetingWorker
import com.example.chat.ui.cards.CardsViewModel
import com.example.chat.ui.cards.PetList
import com.example.chat.ui.chat.ChatScreen
import com.example.chat.ui.chat.PetChatViewModel
import com.example.chat.ui.components.PetAvatar
import com.example.chat.ui.navigation.CardsRoute
import com.example.chat.ui.navigation.ChatRoute
import com.example.chat.ui.navigation.DrawerContent
import com.example.chat.ui.navigation.NotesRoute
import com.example.chat.ui.navigation.SessionListRoute
import com.example.chat.ui.navigation.SettingsRoute
import com.example.chat.ui.navigation.SocialRoute
import com.example.chat.ui.navigation.TOP_LEVEL_ROUTES
import com.example.chat.ui.navigation.TopLevelBackStack
import com.example.chat.ui.notes.NotesScreen
import com.example.chat.ui.session.SessionListScreen
import com.example.chat.ui.settings.SettingsScreen
import com.example.chat.ui.social.SocialScreen
import com.example.chat.ui.util.WindowSize
import com.example.chat.ui.util.rememberWindowSizeClass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetChatApp(
    viewModel: PetChatViewModel = hiltViewModel(),
    cardsViewModel: CardsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val topLevelBackStack = remember { TopLevelBackStack<Any>(ChatRoute) }

    var currentPetType by remember { mutableStateOf(PetType.CAT) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showPetSelector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAllSessions()
        viewModel.resetScroll()
    }

    LaunchedEffect(currentPetType) {
        PetGreetingWorker.savePetType(context, currentPetType)
    }

    val windowSize = rememberWindowSizeClass()

    MaterialTheme {
        ModalNavigationDrawer(
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        onNavigateToSessionList = {
                            topLevelBackStack.add(SessionListRoute)
                            scope.launch { drawerState.close() }
                        },
                        onNavigateToSettings = {
                            topLevelBackStack.add(SettingsRoute)
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            },
            drawerState = drawerState,
            gesturesEnabled = true,
            scrimColor = Color.Black.copy(alpha = 0.32f)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (windowSize != WindowSize.Compact) {
                    PetChatNavigationRail(
                        topLevelKey = topLevelBackStack.topLevelKey,
                        onRouteSelected = { route ->
                            topLevelBackStack.addTopLevel(route)
                        }
                    )
                }

                Scaffold(
                    topBar = {
                        PetChatTopBar(
                            topLevelKey = topLevelBackStack.topLevelKey,
                            showPetSelector = showPetSelector,
                            onTogglePetSelector = { showPetSelector = !showPetSelector },
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                    },
                    bottomBar = {
                        if (windowSize == WindowSize.Compact &&
                            topLevelBackStack.topLevelKey != SessionListRoute &&
                            topLevelBackStack.topLevelKey != SettingsRoute) {
                            PetChatBottomBar(
                                topLevelKey = topLevelBackStack.topLevelKey,
                                onRouteSelected = { route ->
                                    topLevelBackStack.addTopLevel(route)
                                }
                            )
                        }
                    },
                    containerColor = ScaffoldBackground,
                    modifier = Modifier.weight(1f)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        PetSelectorOverlay(
                            visible = showPetSelector && topLevelBackStack.topLevelKey == ChatRoute,
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
                                .consumeWindowInsets(innerPadding)
                                .clickable(
                                    enabled = showPetSelector,
                                    onClick = { showPetSelector = false },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                        ) {
                            NavDisplay(
                                backStack = topLevelBackStack.backStack,
                                onBack = { topLevelBackStack.removeLast() },
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                                predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
                                entryProvider = entryProvider {
                                    entry<ChatRoute> {
                                        ChatScreen(
                                            viewModel = viewModel,
                                            petType = currentPetType,
                                            showPetSelector = showPetSelector,
                                            onHidePetSelector = { showPetSelector = false }
                                        )
                                    }
                                    entry<CardsRoute> {
                                        val pets by cardsViewModel.pets.collectAsState()
                                        PetList(
                                            pets = pets,
                                            onNavigateToChat = { petType ->
                                                currentPetType = petType
                                                topLevelBackStack.addTopLevel(ChatRoute)
                                            }
                                        )
                                    }
                                    entry<NotesRoute> {
                                        NotesScreen()
                                    }
                                    entry<SocialRoute> {
                                        SocialScreen()
                                    }
                                    entry<SessionListRoute> {
                                        SessionListScreen(
                                            viewModel = viewModel,
                                            onSessionSelected = { sessionId ->
                                                viewModel.switchToSession(sessionId)
                                                topLevelBackStack.addTopLevel(ChatRoute)
                                            }
                                        )
                                    }
                                    entry<SettingsRoute> {
                                        SettingsScreen(
                                            settingsManager = settingsManager,
                                            onBack = { topLevelBackStack.removeLast() }
                                        )
                                    }
                                }
                            )
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
    topLevelKey: Any,
    showPetSelector: Boolean,
    onTogglePetSelector: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val isPetSelectorActive = showPetSelector && topLevelKey == ChatRoute

    TopAppBar(
        title = {
            when {
                isPetSelectorActive -> Text(
                    stringResource(R.string.topbar_pet_selector_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                topLevelKey == ChatRoute -> Text("")
                topLevelKey == CardsRoute -> {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        Text(stringResource(R.string.topbar_cards), modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AccentOrange)
                    }
                }
                topLevelKey == NotesRoute -> {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        Text(stringResource(R.string.topbar_notes), modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AccentOrange)
                    }
                }
                topLevelKey == SocialRoute -> {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        Text(stringResource(R.string.topbar_social), modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AccentOrange)
                    }
                }
                topLevelKey == SettingsRoute -> {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        Text(stringResource(R.string.settings_title), modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AccentOrange)
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.padding(start = 8.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.sidebar),
                    contentDescription = stringResource(R.string.topbar_open_drawer),
                    modifier = Modifier.size(24.dp),
                    tint = if (isPetSelectorActive) Color.White else Color.Unspecified
                )
            }
        },
        actions = {
            if (topLevelKey == ChatRoute) {
                val rotation by animateFloatAsState(
                    targetValue = if (showPetSelector) 180f else 0f,
                    animationSpec = tween(durationMillis = 300)
                )
                IconButton(onClick = onTogglePetSelector, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow),
                        contentDescription = if (showPetSelector) stringResource(R.string.topbar_close_pet_selector) else stringResource(R.string.topbar_toggle_pet),
                        modifier = Modifier.size(24.dp).rotate(rotation),
                        tint = if (showPetSelector) Color.White else Color.Unspecified
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isPetSelectorActive) PetSelectorBackground else Color.White,
            titleContentColor = if (isPetSelectorActive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = if (isPetSelectorActive) Color.White else Color.Unspecified,
            actionIconContentColor = if (isPetSelectorActive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun PetChatBottomBar(
    topLevelKey: Any,
    onRouteSelected: (Any) -> Unit
) {
    NavigationBar(
        containerColor = BottomBarBackground,
        contentColor = BottomBarContent,
        modifier = Modifier.heightIn(min = 72.dp, max = 96.dp)
    ) {
        TOP_LEVEL_ROUTES.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = if (topLevelKey == item.route) item.selectedIcon else item.unselectedIcon),
                        contentDescription = item.title,
                        tint = if (topLevelKey == item.route) AccentOrange else Color.Gray,
                        modifier = Modifier.size(26.dp)
                    )
                },
                label = {
                    Text(
                        item.title,
                        fontSize = 12.sp,
                        fontWeight = if (topLevelKey == item.route) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = topLevelKey == item.route,
                onClick = { onRouteSelected(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentOrange,
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = AccentOrange,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun PetChatNavigationRail(
    topLevelKey: Any,
    onRouteSelected: (Any) -> Unit
) {
    NavigationRail(
        containerColor = BottomBarBackground,
        contentColor = BottomBarContent,
        modifier = Modifier.background(BottomBarBackground)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        TOP_LEVEL_ROUTES.forEach { item ->
            NavigationRailItem(
                icon = {
                    Icon(
                        painter = painterResource(id = if (topLevelKey == item.route) item.selectedIcon else item.unselectedIcon),
                        contentDescription = item.title,
                        tint = if (topLevelKey == item.route) AccentOrange else Color.Gray,
                        modifier = Modifier.size(26.dp)
                    )
                },
                label = {
                    Text(
                        item.title,
                        fontSize = 12.sp,
                        fontWeight = if (topLevelKey == item.route) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = topLevelKey == item.route,
                onClick = { onRouteSelected(item.route) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = AccentOrange,
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = AccentOrange,
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
    currentPetType: PetType,
    onSelect: (PetType) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(PetSelectorBackground)
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
                PetAvatar(name = "布丁", imageRes = R.drawable.pet_cat, isSelected = currentPetType == PetType.CAT, onClick = { onSelect(PetType.CAT) })
                PetAvatar(name = "大白", imageRes = R.drawable.pet_samoyed, isSelected = currentPetType == PetType.DOG, onClick = { onSelect(PetType.DOG) })
                PetAvatar(name = "豆豆", imageRes = R.drawable.pet_shiba, isSelected = currentPetType == PetType.SHIBA, onClick = { onSelect(PetType.SHIBA) })
                PetAvatar(name = "团绒", imageRes = R.drawable.pet_hamster, isSelected = currentPetType == PetType.HAMSTER, onClick = { onSelect(PetType.HAMSTER) })
            }
        }
    }
}
