package com.example.chat.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TopLevelBackStack<T : Any>(private val baseKey: T) {

    var topLevelKey by mutableStateOf(baseKey)
        private set

    val backStack = mutableStateListOf(baseKey)

    fun addTopLevel(key: T) {
        if (key == baseKey) {
            backStack.clear()
            backStack.add(baseKey)
        } else {
            backStack.clear()
            backStack.add(baseKey)
            backStack.add(key)
        }
        topLevelKey = key
    }

    fun add(key: T) {
        backStack.add(key)
        topLevelKey = key
    }

    fun removeLast() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
        topLevelKey = backStack.last()
    }
}
