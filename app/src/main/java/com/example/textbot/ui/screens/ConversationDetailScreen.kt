package com.example.textbot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(groupedMessages) { groupedSms ->
                    MessageBubble(groupedSms)
                }
            }
        }
    }
}
