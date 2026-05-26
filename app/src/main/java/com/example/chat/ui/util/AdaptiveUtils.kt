package com.example.chat.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "Desktop", device = Devices.DESKTOP, showBackground = true)
annotation class FormFactorPreviews

enum class WindowSize {
    Compact,  // < 600dp (most phones portrait)
    Medium,   // 600dp ~ 840dp (foldables, small tablets)
    Expanded  // >= 840dp (tablets, desktop)
}

@Composable
@Suppress("ConfigurationScreenWidthHeight")
fun rememberWindowSizeClass(): WindowSize {
    val configuration = LocalConfiguration.current
    return when {
        configuration.screenWidthDp < 600 -> WindowSize.Compact
        configuration.screenWidthDp < 840 -> WindowSize.Medium
        else -> WindowSize.Expanded
    }
}

data class AppDimensions(
    val screenPadding: Dp,
    val itemSpacing: Dp,
    val cardCornerRadius: Dp
)

val CompactDimensions = AppDimensions(
    screenPadding = 16.dp,
    itemSpacing = 8.dp,
    cardCornerRadius = 12.dp
)

val ExpandedDimensions = AppDimensions(
    screenPadding = 24.dp,
    itemSpacing = 16.dp,
    cardCornerRadius = 16.dp
)

@Composable
fun rememberAppDimensions(windowSize: WindowSize): AppDimensions {
    return when (windowSize) {
        WindowSize.Compact -> CompactDimensions
        WindowSize.Medium, WindowSize.Expanded -> ExpandedDimensions
    }
}
