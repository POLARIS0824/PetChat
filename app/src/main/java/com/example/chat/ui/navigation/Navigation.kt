package com.example.chat.ui.navigation

import com.example.chat.R

enum class Screen {
    Chat, Cards, Notes, Social, SessionList
}

data class NavItem(
    val screen: Screen,
    val title: String,
    val unselectedIcon: Int,
    val selectedIcon: Int
)

val BottomNavItems = listOf(
    NavItem(Screen.Chat, "聊天", R.drawable.chat_outline, R.drawable.chat_fill),
    NavItem(Screen.Cards, "名片夹", R.drawable.par_outline, R.drawable.par_fill),
    NavItem(Screen.Notes, "便利贴", R.drawable.bag_outline, R.drawable.bag_fill),
    NavItem(Screen.Social, "萌友圈", R.drawable.adopt_outline, R.drawable.adopt_fill)
)
