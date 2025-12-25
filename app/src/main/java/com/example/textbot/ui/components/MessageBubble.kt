package com.example.textbot.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.textbot.data.model.SmsMessage
import android.provider.Telephony
import java.util.*
import java.text.DateFormat
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MessageBubble(
    groupedSms: GroupedSms,
    onVideoClick: (String) -> Unit = {},
    onDownloadClick: (Attachment) -> Unit = {}
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedAttachment by remember { mutableStateOf<Attachment?>(null) }
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
                // Render attachments
                message.attachments.forEach { attachment ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .combinedClickable(
                                onClick = {
                                    if (attachment.contentType.startsWith("video/")) {
                                        onVideoClick(attachment.uri)
                                    }
                                },
                                onLongClick = {
                                    selectedAttachment = attachment
                                    showBottomSheet = true
                                }
                            )
                    ) {
                        when {
                            attachment.contentType.startsWith("image/") -> {
                                AsyncImage(
                                    model = attachment.uri,
                                    contentDescription = stringResource(R.string.content_description_image_attachment),
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            attachment.contentType.startsWith("video/") -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .aspectRatio(16/9f)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = attachment.uri,
                                        contentDescription = stringResource(R.string.attachment_video),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Overlay for better icon visibility
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = stringResource(R.string.content_description_play),
                                        modifier = Modifier.size(48.dp),
                                        tint = Color.White
                                    )
                                    Text(
                                        text = stringResource(R.string.attachment_video),
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                            attachment.contentType.startsWith("audio/") -> {
                                AudioPlayer(
                                    uri = attachment.uri,
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                )
                            }
                            else -> {
                                // Other files
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.1f))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = stringResource(R.string.attachment_file),
                                        tint = textColor
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = attachment.fileName ?: stringResource(R.string.attachment_file),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textColor,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = attachment.contentType,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
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

    // Bottom Sheet for attachment actions
    if (showBottomSheet && selectedAttachment != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = selectedAttachment?.fileName ?: stringResource(R.string.attachment_file),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                
                // Download option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedAttachment?.let { onDownloadClick(it) }
                            showBottomSheet = false
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(R.string.action_download)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.action_download),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

private fun formatDateTime(timeInMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    return formatter.format(Date(timeInMillis))
}
