package com.example.chat.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.chat.ui.theme.AccentOrange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PetAvatar(
    name: String,
    imageRes: Int,
    onClick: () -> Unit,
    isSelected: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = name,
                modifier = Modifier
                    .size(LocalConfiguration.current.screenWidthDp.dp * 0.2f)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-1).dp, y = (-1).dp)
                        .background(AccentOrange, CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) Color.Yellow else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
