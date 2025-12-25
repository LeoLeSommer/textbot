package com.example.textbot.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import android.provider.Telephony
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.textbot.data.model.SmsMessage
import java.util.*
import java.text.DateFormat
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.res.stringResource
import com.example.textbot.R
import com.example.textbot.data.model.Attachment

enum class BubblePosition {
    START, MIDDLE, END, SINGLE
}

data class GroupedSms(
    val message: SmsMessage,
    val position: BubblePosition,
    val showTimestamp: Boolean
)

// Helper to check if a message type corresponds to the user (sent/sending/failed)
fun isMessageFromMe(type: Int): Boolean {
    return type == Telephony.Sms.MESSAGE_TYPE_SENT ||
           type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
           type == Telephony.Sms.MESSAGE_TYPE_FAILED ||
           type == Telephony.Sms.MESSAGE_TYPE_QUEUED
}

fun groupMessages(messages: List<SmsMessage>): List<GroupedSms> {
    if (messages.isEmpty()) return emptyList()
    val grouped = mutableListOf<GroupedSms>()
    val threshold = 5 * 60 * 1000L // 5 minutes

    for (i in messages.indices) {
        val current = messages[i]
        val prev = if (i > 0) messages[i - 1] else null
        val next = if (i < messages.size - 1) messages[i + 1] else null

        // Check if messages occupy the same "side" (both me or both other)
        val isPrevSameSide = prev != null && (isMessageFromMe(prev.type) == isMessageFromMe(current.type))
        val isPrevClose = prev != null && (current.date - prev.date) < threshold
        val isStart = !isPrevSameSide || !isPrevClose

        val isNextSameSide = next != null && (isMessageFromMe(next.type) == isMessageFromMe(current.type))
        val isNextClose = next != null && (next.date - current.date) < threshold
        val isEnd = !isNextSameSide || !isNextClose

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

@Composable
fun MessageBubble(groupedSms: GroupedSms) {
    val message = groupedSms.message
    val isMe = isMessageFromMe(message.type)
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
            Column {
                // Render image attachments
                message.attachments.forEach { attachment ->
                    if (attachment.contentType.startsWith("image/")) {
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = stringResource(R.string.content_description_image_attachment),
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth(0.7f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                
                // Render text body if not empty
                if (message.body.isNotEmpty()) {
                    Text(
                        text = message.body,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        if (groupedSms.showTimestamp) {
            Row(
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateTime(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    when (message.type) {
                        Telephony.Sms.MESSAGE_TYPE_OUTBOX -> {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Telephony.Sms.MESSAGE_TYPE_SENT -> {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Telephony.Sms.MESSAGE_TYPE_FAILED -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDateTime(timeInMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    return formatter.format(Date(timeInMillis))
}
