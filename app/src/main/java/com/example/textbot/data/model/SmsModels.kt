package com.example.textbot.data.model

data class Attachment(
    val uri: String,
    val contentType: String,
    val fileName: String? = null,
    val fileSize: Long? = null
)

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String, // Phone number
    val body: String,
    val date: Long,
    val type: Int, // 1 = Inbox, 2 = Sent for SMS. For MMS see Telephony.BaseMmsColumns.MESSAGE_BOX
    val read: Int, // 0 = Unread, 1 = Read
    val isMms: Boolean = false,
    val attachments: List<Attachment> = emptyList()
)

data class Conversation(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val contactLookupUri: String?,
    val lastMessage: String,
    val lastMessageDate: Long,
    val unreadCount: Int = 0,
    val photoUri: String? = null
)
