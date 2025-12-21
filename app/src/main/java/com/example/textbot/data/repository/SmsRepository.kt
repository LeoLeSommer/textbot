package com.example.textbot.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.example.textbot.data.model.Conversation
import com.example.textbot.data.model.SmsMessage

class SmsRepository(private val context: Context) {

    fun getAllConversations(): List<Conversation> {
        val conversations = mutableMapOf<String, MutableList<SmsMessage>>()
        val contentResolver: ContentResolver = context.contentResolver
        
        val cursor: Cursor? = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
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
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val address = it.getString(addressIndex) ?: "Unknown"
                val body = it.getString(bodyIndex) ?: ""
                val date = it.getLong(dateIndex)
                val id = it.getLong(idIndex)
                val type = it.getInt(typeIndex)
                val read = it.getInt(readIndex)

                val message = SmsMessage(id, address, body, date, type, read)
                conversations.getOrPut(address) { mutableListOf() }.add(message)
            }
        }

        return conversations.map { (address, messages) ->
            val lastMsg = messages.first() // Sorted by default sort order (descending date)
            val contactInfo = getContactInfo(address)
            Conversation(
                address = address,
                contactName = contactInfo.name,
                contactLookupUri = contactInfo.lookupUri,
                lastMessage = lastMsg.body,
                lastMessageDate = lastMsg.date,
                unreadCount = messages.count { it.read == 0 && it.type == Telephony.Sms.MESSAGE_TYPE_INBOX }
            )
        }.sortedByDescending { it.lastMessageDate }
    }

    fun getMessagesForAddress(address: String): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val contentResolver: ContentResolver = context.contentResolver
        
        val cursor: Cursor? = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(address),
            "${Telephony.Sms.DATE} ASC"
        )

        cursor?.use {
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val body = it.getString(bodyIndex) ?: ""
                val date = it.getLong(dateIndex)
                val id = it.getLong(idIndex)
                val type = it.getInt(typeIndex)
                val read = it.getInt(readIndex)

                messages.add(SmsMessage(id, address, body, date, type, read))
            }
        }
        return messages
    }

    private data class ContactInfo(val name: String?, val lookupUri: String?)

    private fun getContactInfo(phoneNumber: String): ContactInfo {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.LOOKUP_KEY,
            ContactsContract.PhoneLookup._ID
        )
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val lookupKeyIndex = it.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                val idIndex = it.getColumnIndex(ContactsContract.PhoneLookup._ID)
                
                val name = if (nameIndex != -1) it.getString(nameIndex) else null
                val lookupKey = if (lookupKeyIndex != -1) it.getString(lookupKeyIndex) else null
                val id = if (idIndex != -1) it.getLong(idIndex) else null
                
                val lookupUri = if (lookupKey != null && id != null) {
                    ContactsContract.Contacts.getLookupUri(id, lookupKey).toString()
                } else null
                
                return ContactInfo(name, lookupUri)
            }
        }
        return ContactInfo(null, null)
    }
}
