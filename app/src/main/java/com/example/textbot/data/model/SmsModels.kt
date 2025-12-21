package com.example.textbot.data.model

data class SmsMessage(
    val id: Long,
    val address: String, // Phone number
    val body: String,
    val date: Long,
    val type: Int, // 1 = Inbox, 2 = Sent
    val read: Int // 0 = Unread, 1 = Read
)

data class Conversation(
    val address: String,
    val contactName: String?,
    val contactLookupUri: String?,
    val lastMessage: String,
    val lastMessageDate: Long,
    val unreadCount: Int = 0
)
