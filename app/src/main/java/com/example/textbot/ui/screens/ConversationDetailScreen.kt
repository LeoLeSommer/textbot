package com.example.textbot.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.shape.CircleShape
import coil3.compose.AsyncImage
import com.example.textbot.R
import com.example.textbot.ui.components.MessageBubble
import com.example.textbot.ui.components.groupMessages
import com.example.textbot.ui.viewmodel.SmsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    threadId: Long,
    viewModel: SmsViewModel,
    onBackClick: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val conversation = remember(conversations, threadId) {
        conversations.find { it.threadId == threadId }
    }
    val contactName = conversation?.contactName
    val messages by viewModel.messages.collectAsState()
    
    // Get address from conversation, fallback to first message if empty
    val address = remember(conversation, messages) {
        val conversationAddress = conversation?.address ?: ""
        if (conversationAddress.isBlank() && messages.isNotEmpty()) {
            // Try to get address from the first message that has one
            messages.firstOrNull { it.address.isNotBlank() }?.address ?: conversationAddress
        } else {
            conversationAddress
        }
    }
    
    val isLoading by viewModel.loading.collectAsState()

    LaunchedEffect(threadId) {
        viewModel.loadMessages(threadId)
        viewModel.markAsRead(threadId)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, threadId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.markAsRead(threadId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(threadId) {
        viewModel.setCurrentThreadId(threadId)
        onDispose {
            viewModel.setCurrentThreadId(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            if (conversation?.photoUri != null) {
                                AsyncImage(
                                    model = conversation.photoUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = contactName ?: address.ifEmpty { stringResource(R.string.unknown_sender) },
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (contactName != null) {
                                Text(
                                    text = address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                },
                actions = {
                    val context = LocalContext.current
                    var showMenu by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address"))
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.Call, contentDescription = stringResource(R.string.action_call))
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            val label = if (conversation?.contactLookupUri != null) {
                                stringResource(R.string.action_view_contact)
                            } else {
                                stringResource(R.string.action_add_to_contacts)
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showMenu = false
                                    val intent = if (conversation?.contactLookupUri != null) {
                                        Intent(Intent.ACTION_VIEW, Uri.parse(conversation.contactLookupUri))
                                    } else {
                                        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                            putExtra(ContactsContract.Intents.Insert.PHONE, address)
                                        }
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        bottomBar = {
            com.example.textbot.ui.components.MessageComposer(
                onSendMessage = { body, attachments ->
                    viewModel.sendMessageWithAttachments(address, body, attachments)
                }
            )
        }
    ) { padding ->
        if (isLoading && messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val groupedMessages = remember(messages) { groupMessages(messages).reversed() }
            val listState = rememberLazyListState()

            // Auto-scroll to index 0 (visual bottom) when new messages arrive
            LaunchedEffect(groupedMessages.firstOrNull()?.message?.id) {
                if (groupedMessages.isNotEmpty()) {
                    val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                    val isAtBottom = lastVisibleItemIndex != null
                    
                    if (isAtBottom) {
                        listState.animateScrollToItem(0)
                    }
                }
            }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = groupedMessages,
                    key = { "${it.message.id}_${it.message.isMms}" }
                ) { groupedSms ->
                    Box(modifier = Modifier.animateItem()) {
                        MessageBubble(groupedSms)
                    }
                }
            }
        }
    }
}
