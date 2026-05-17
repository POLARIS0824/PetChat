package com.example.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chat.model.PetTypes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(viewModel: PetChatViewModel, onSessionSelected: (String) -> Unit) {
    val sessions by viewModel.allSessions.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("我的宠物聊天") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Text(
            text = "选择一个宠物开始聊天",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )

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

fun getPetAvatar(petType: PetTypes): Int {
    return when (petType) {
        PetTypes.CAT -> R.drawable.pet_cat
        PetTypes.DOG -> R.drawable.pet_samoyed
        PetTypes.HAMSTER -> R.drawable.pet_hamster
        PetTypes.DOG2 -> R.drawable.pet_shiba
    }
}

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
