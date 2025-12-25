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
import com.example.textbot.data.model.Conversation
import com.example.textbot.data.model.SmsMessage

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
        
        val cursor: Cursor? = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            null,
            null,
            Telephony.Sms.DEFAULT_SORT_ORDER
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val threadId = it.getLong(threadIdIndex)
                val address = it.getString(addressIndex) ?: "Unknown"
                val body = it.getString(bodyIndex) ?: ""
                val date = it.getLong(dateIndex)
                val type = it.getInt(typeIndex)
                val read = it.getInt(readIndex)

                val message = SmsMessage(id, threadId, address, body, date, type, read)
                conversations.getOrPut(threadId) { mutableListOf() }.add(message)
            }
        }

        return conversations.map { (threadId, messages) ->
            val lastMsg = messages.first() // Sorted by default sort order (descending date)
            val contactInfo = getContactInfo(lastMsg.address)
            Conversation(
                threadId = threadId,
                address = lastMsg.address,
                contactName = contactInfo.name,
                contactLookupUri = contactInfo.lookupUri,
                lastMessage = lastMsg.body,
                lastMessageDate = lastMsg.date,
                unreadCount = messages.count { it.read == 0 && it.type == Telephony.Sms.MESSAGE_TYPE_INBOX },
                photoUri = contactInfo.photoUri
            )
        }.sortedByDescending { it.lastMessageDate }
    }

    fun getMessagesForThread(threadId: Long): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val contentResolver: ContentResolver = context.contentResolver
        
        val cursor: Cursor? = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val tId = it.getLong(threadIdIndex)
                val address = it.getString(addressIndex) ?: ""
                val body = it.getString(bodyIndex) ?: ""
                val date = it.getLong(dateIndex)
                val type = it.getInt(typeIndex)
                val read = it.getInt(readIndex)

                messages.add(SmsMessage(id, tId, address, body, date, type, read))
            }
        }
        return messages
    }

    data class ContactInfo(val name: String?, val lookupUri: String?, val photoUri: String?)

    fun getContactInfo(phoneNumber: String): ContactInfo {
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
