package com.example.textbot.data.repository

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.example.textbot.data.model.Attachment
import com.example.textbot.data.model.Conversation
import com.example.textbot.data.model.SmsMessage
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction

class SmsRepository(private val context: Context) {
    companion object {
        const val SMS_SENT_ACTION = "com.example.textbot.SMS_SENT"
    }
    
    fun markAsRead(threadId: Long) {
        val contentValues = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
        }
        val selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0"
        val selectionArgs = arrayOf(threadId.toString())
        
        val rows = context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            contentValues,
            selection,
            selectionArgs
        )
        Log.d("SmsRepository", "Marked $rows messages as read for thread $threadId")
    }

    fun getAllConversations(): List<Conversation> {
        val conversations = mutableMapOf<Long, MutableList<SmsMessage>>()
        val contentResolver: ContentResolver = context.contentResolver
        
        // Querying from MmsSms provider to get both SMS and MMS
        val uri = Uri.parse("content://mms-sms/conversations")
        val cursor: Cursor? = contentResolver.query(
            uri,
            null, // Query all columns for mixed provider
            null,
            null,
            "date DESC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)
            val ctTypeIndex = it.getColumnIndex("ct_t") // MMS content type

            while (it.moveToNext()) {
                val threadId = if (threadIdIndex != -1) it.getLong(threadIdIndex) else 0L
                if (conversations.containsKey(threadId)) continue // Already processed newest message for this thread

                val id = if (idIndex != -1) it.getLong(idIndex) else 0L
                val address = if (addressIndex != -1) it.getString(addressIndex) ?: "" else ""
                var date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                val type = if (typeIndex != -1) it.getInt(typeIndex) else 1
                val read = if (readIndex != -1) it.getInt(readIndex) else 0
                val ctType = if (ctTypeIndex != -1) it.getString(ctTypeIndex) else null
                val isMms = ctType != null && ctType.contains("multipart")

                // Normalize date: MMS date is in seconds, SMS is in ms
                if (isMms && date < 1000000000000L) {
                    date *= 1000
                }

                var body = ""
                var attachments = emptyList<Attachment>()

                if (isMms) {
                    val mmsInfo = getMmsAttachments(id)
                    body = mmsInfo.first
                    attachments = mmsInfo.second
                } else {
                    body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                }

                val message = SmsMessage(id, threadId, address, body, date, type, read, isMms, attachments)
                conversations.getOrPut(threadId) { mutableListOf() }.add(message)
            }
        }

        return conversations.map { (threadId, messages) ->
            val lastMsg = messages.first()
            val contactInfo = getContactInfo(lastMsg.address)
            Conversation(
                threadId = threadId,
                address = lastMsg.address,
                contactName = contactInfo.name,
                contactLookupUri = contactInfo.lookupUri,
                lastMessage = lastMsg.body,
                lastMessageDate = lastMsg.date,
                unreadCount = messages.count { it.read == 0 && (it.type == Telephony.Sms.MESSAGE_TYPE_INBOX || it.type == 1) }, // 1 is MESSAGE_BOX_INBOX for MMS
                photoUri = contactInfo.photoUri
            )
        }.sortedByDescending { it.lastMessageDate }
    }

    fun getMessagesForThread(threadId: Long): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val contentResolver: ContentResolver = context.contentResolver
        
        val uri = Uri.parse("content://mms-sms/conversations/$threadId")
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            "ct_t"
        )
        
        val cursor: Cursor? = contentResolver.query(
            uri,
            projection,
            null,
            null,
            "date ASC"
        )

        cursor?.use {
            Log.d("SmsRepository", "getMessagesForThread: Found ${it.count} messages for thread $threadId")
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE) // Works for SMS, for MMS it might be MESSAGE_BOX
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)
            val ctTypeIndex = it.getColumnIndex("ct_t")

            while (it.moveToNext()) {
                val id = if (idIndex != -1) it.getLong(idIndex) else 0L
                var date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                
                // Handle separate type columns for SMS and MMS in unified view if needed
                val type = if (typeIndex != -1) it.getInt(typeIndex) else 1 // Default to Inbox
                val read = if (readIndex != -1) it.getInt(readIndex) else 0
                
                val ctType = if (ctTypeIndex != -1) it.getString(ctTypeIndex) else null
                val isMms = ctType != null && ctType.contains("multipart")
                
                // Normalize date
                if (isMms && date < 1000000000000L) {
                    date *= 1000
                }
                
                var address = if (addressIndex != -1) it.getString(addressIndex) ?: "" else ""
                var body = ""
                var attachments = emptyList<Attachment>()

                if (isMms) {
                    val mmsInfo = getMmsAttachments(id)
                    body = mmsInfo.first
                    attachments = mmsInfo.second
                    // Address for MMS is in a different table (addr)
                    if (address.isEmpty() || address == "insert-address-token") {
                        address = getMmsAddress(id)
                    }
                } else {
                    body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                }

                messages.add(SmsMessage(id, threadId, address, body, date, type, read, isMms, attachments))
            }
        }
        return messages.sortedBy { it.date }
    }

    private fun getMmsAttachments(mmsId: Long): Pair<String, List<Attachment>> {
        val attachments = mutableListOf<Attachment>()
        var textBody = ""
        val selection = "mid = ?"
        val selectionArgs = arrayOf(mmsId.toString())
        val cursor = context.contentResolver.query(
            Uri.parse("content://mms/part"),
            null,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            val ctIndex = it.getColumnIndex("ct")
            val textIndex = it.getColumnIndex("text")
            val idIndex = it.getColumnIndex("_id")
            val nameIndex = it.getColumnIndex("name")
            val clIndex = it.getColumnIndex("cl") // Content-Location often has the filename

            while (it.moveToNext()) {
                val contentType = it.getString(ctIndex) ?: continue
                if ("text/plain" == contentType) {
                    textBody = it.getString(textIndex) ?: ""
                } else if (!contentType.contains("smil", ignoreCase = true)) {
                    // Include any non-smil attachment (images, videos, audio, vcards, etc.)
                    val partId = it.getLong(idIndex)
                    val uri = "content://mms/part/$partId"
                    val fileName = it.getString(nameIndex) ?: it.getString(clIndex)
                    
                    attachments.add(Attachment(
                        uri = uri,
                        contentType = contentType,
                        fileName = fileName
                    ))
                }
            }
        }
        return Pair(textBody, attachments)
    }

    /**
     * Saves an attachment to the system Downloads folder.
     */
    fun saveAttachmentToDownloads(attachment: Attachment): Uri? {
        val cr = context.contentResolver
        val sourceUri = Uri.parse(attachment.uri)
        
        try {
            val extension = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(attachment.contentType) ?: ""
            
            val fileName = attachment.fileName ?: "mms_attachment_${System.currentTimeMillis()}"
            val fullName = if (extension.isNotEmpty() && !fileName.endsWith(".$extension")) {
                "$fileName.$extension"
            } else {
                fileName
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fullName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, attachment.contentType)
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }

                val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val itemUri = cr.insert(collection, values) ?: return null

                cr.openOutputStream(itemUri)?.use { outputStream ->
                    cr.openInputStream(sourceUri)?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                cr.update(itemUri, values, null, null)
                return itemUri
            } else {
                // Legacy approach for API < 29
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val destFile = java.io.File(downloadsDir, fullName)
                
                cr.openInputStream(sourceUri)?.use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // Trigger media scanner
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(attachment.contentType), null)
                
                return Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error saving attachment", e)
            return null
        }
    }

    private fun getMmsAddress(mmsId: Long): String {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        val cursor = context.contentResolver.query(uri, null, "msg_id = ?", arrayOf(mmsId.toString()), null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(it.getColumnIndex("address")) ?: ""
            }
        }
        return ""
    }


    data class ContactInfo(val name: String?, val lookupUri: String?, val photoUri: String?)

    fun getContactInfo(phoneNumber: String): ContactInfo {
        // Guard against empty phone numbers
        if (phoneNumber.isBlank()) {
            return ContactInfo(null, null, null)
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.LOOKUP_KEY,
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val lookupKeyIndex = it.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                val idIndex = it.getColumnIndex(ContactsContract.PhoneLookup._ID)
                
                val photoUriIndex = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                
                val name = if (nameIndex != -1) it.getString(nameIndex) else null
                val lookupKey = if (lookupKeyIndex != -1) it.getString(lookupKeyIndex) else null
                val id = if (idIndex != -1) it.getLong(idIndex) else null
                val photoUri = if (photoUriIndex != -1) it.getString(photoUriIndex) else null
                
                val lookupUri = if (lookupKey != null && id != null) {
                    ContactsContract.Contacts.getLookupUri(id, lookupKey).toString()
                } else null
                
                return ContactInfo(name, lookupUri, photoUri)
            }
        }
        return ContactInfo(null, null, null)
    }

    fun getThreadIdForAddress(address: String): Long? {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error getting thread ID for address: $address", e)
            null
        }
    }

    fun sendMessage(address: String, body: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // 1. Insert into system "Outbox" database first (Sending state)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            }
            val uri = context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            
            // 2. Prepare PendingIntent for status callback
            val sentIntent = PendingIntent.getBroadcast(
                context,
                uri.hashCode(),
                Intent(SMS_SENT_ACTION).apply {
                    data = uri
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // 3. Send the SMS
            smsManager.sendTextMessage(address, null, body, sentIntent, null)
            
            Log.d("SmsRepository", "Message inserted into outbox and sending triggered for $address")
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to send SMS to $address", e)
            throw e
        }
    }

    fun sendMms(address: String, body: String, attachments: List<Attachment>) {
        try {
            val settings = Settings().apply {
                useSystemSending = true // Let system handle APNs if possible on newer Android
            }

            val transaction = Transaction(context, settings)
            val message = Message(body, address)

            attachments.forEach { attachment ->
                // Basic conversion: Assuming file URI or content URI that can be read
                // The library handles content URIs well.
                try {
                     val bytes = context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { it.readBytes() }
                     if (bytes != null) {
                         message.addMedia(bytes, attachment.contentType)
                     }
                } catch (e: Exception) {
                    Log.e("SmsRepository", "Failed to read attachment: \${attachment.uri}", e)
                }
            }

            transaction.sendNewMessage(message, Transaction.NO_THREAD_ID)
            Log.d("SmsRepository", "MMS sent via klinker library to $address")

        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to send MMS", e)
        }
    }

    fun updateMessageStatus(uri: Uri, type: Int) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.TYPE, type)
            }
            context.contentResolver.update(uri, values, null, null)
            Log.d("SmsRepository", "Message $uri status updated to $type")
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to update status for $uri", e)
        }
    }
}
