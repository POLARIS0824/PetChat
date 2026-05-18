package com.example.chat.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.example.chat.R
import kotlinx.serialization.Serializable

@Serializable
data object ChatRoute : NavKey

@Serializable
data object CardsRoute : NavKey

@Serializable
data object NotesRoute : NavKey

@Serializable
data object SocialRoute : NavKey

@Serializable
data object SessionListRoute : NavKey

val TOP_LEVEL_ROUTES: List<TopLevelRouteItem> = listOf(
    TopLevelRouteItem(ChatRoute, "聊天", R.drawable.chat_outline, R.drawable.chat_fill),
    TopLevelRouteItem(CardsRoute, "名片夹", R.drawable.par_outline, R.drawable.par_fill),
    TopLevelRouteItem(NotesRoute, "便利贴", R.drawable.bag_outline, R.drawable.bag_fill),
    TopLevelRouteItem(SocialRoute, "萌友圈", R.drawable.adopt_outline, R.drawable.adopt_fill),
)

data class TopLevelRouteItem(
    val route: NavKey,
    val title: String,
    val unselectedIcon: Int,
    val selectedIcon: Int
)
