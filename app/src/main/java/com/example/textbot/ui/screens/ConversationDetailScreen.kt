package com.example.textbot.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.textbot.R
import com.example.textbot.ui.components.MessageBubble
import com.example.textbot.ui.components.groupMessages
import com.example.textbot.ui.viewmodel.SmsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    address: String,
    viewModel: SmsViewModel,
    onBackClick: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val contactName = remember(conversations, address) {
        conversations.find { it.address == address }?.contactName
    }
    
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
                title = { 
                    Column {
                        Text(
                            text = contactName ?: address,
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
                            val conversation = conversations.find { it.address == address }
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
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(groupedMessages) { groupedSms ->
                    MessageBubble(groupedSms)
                }
            }
        }
    }
}
