package com.example.textbot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.textbot.data.SmsMessage
import java.text.SimpleDateFormat
import java.util.*

enum class BubblePosition {
    START, MIDDLE, END, SINGLE
}

data class GroupedSms(
    val message: SmsMessage,
    val position: BubblePosition,
    val showTimestamp: Boolean
)

fun groupMessages(messages: List<SmsMessage>): List<GroupedSms> {
    if (messages.isEmpty()) return emptyList()
    val grouped = mutableListOf<GroupedSms>()
    val threshold = 5 * 60 * 1000L // 5 minutes

    for (i in messages.indices) {
        val current = messages[i]
        val prev = if (i > 0) messages[i - 1] else null
        val next = if (i < messages.size - 1) messages[i + 1] else null

        val isPrevSameSender = prev != null && prev.type == current.type
        val isPrevClose = prev != null && (current.date - prev.date) < threshold
        val isStart = !isPrevSameSender || !isPrevClose

        val isNextSameSender = next != null && next.type == current.type
        val isNextClose = next != null && (next.date - current.date) < threshold
        val isEnd = !isNextSameSender || !isNextClose

        val position = when {
            isStart && isEnd -> BubblePosition.SINGLE
            isStart -> BubblePosition.START
            isEnd -> BubblePosition.END
            else -> BubblePosition.MIDDLE
        }

        grouped.add(GroupedSms(current, position, isEnd))
    }
    return grouped
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    address: String,
    viewModel: SmsViewModel,
    onBackClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.loading.collectAsState()

    LaunchedEffect(address) {
        viewModel.loadMessages(address)
    }

    DisposableEffect(address) {
        viewModel.setCurrentAddress(address)
        onDispose {
            viewModel.setCurrentAddress(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(address) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { padding ->
        if (isLoading && messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val groupedMessages = remember(messages) { groupMessages(messages) }
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp) // Reduced spacing for grouped messages
            ) {
                items(groupedMessages) { groupedSms ->
                    MessageBubble(groupedSms)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(groupedSms: GroupedSms) {
    val message = groupedSms.message
    val isMe = message.type == 2 // Telephony.Sms.MESSAGE_TYPE_SENT
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    
    val baseRadius = 16.dp
    val smallRadius = 4.dp
    
    val shape = if (isMe) {
        when (groupedSms.position) {
            BubblePosition.START -> RoundedCornerShape(baseRadius, baseRadius, smallRadius, baseRadius)
            BubblePosition.MIDDLE -> RoundedCornerShape(baseRadius, smallRadius, smallRadius, baseRadius)
            BubblePosition.END -> RoundedCornerShape(baseRadius, smallRadius, 0.dp, baseRadius)
            BubblePosition.SINGLE -> RoundedCornerShape(baseRadius, baseRadius, 0.dp, baseRadius)
        }
    } else {
        when (groupedSms.position) {
            BubblePosition.START -> RoundedCornerShape(baseRadius, baseRadius, baseRadius, smallRadius)
            BubblePosition.MIDDLE -> RoundedCornerShape(smallRadius, baseRadius, baseRadius, smallRadius)
            BubblePosition.END -> RoundedCornerShape(smallRadius, baseRadius, baseRadius, 0.dp)
            BubblePosition.SINGLE -> RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (groupedSms.position == BubblePosition.START || groupedSms.position == BubblePosition.SINGLE) 4.dp else 0.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bgColor,
            shape = shape,
            tonalElevation = 2.dp
        ) {
            Text(
                text = message.body,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (groupedSms.showTimestamp) {
            Text(
                text = formatDateTime(message.date),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatDateTime(timeInMillis: Long): String {
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return formatter.format(Date(timeInMillis))
}
