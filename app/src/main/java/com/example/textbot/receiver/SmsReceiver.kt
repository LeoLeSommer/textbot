package com.example.textbot.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.textbot.MainActivity
import com.example.textbot.data.repository.SmsRepository

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "sms_notifications"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val repository = SmsRepository(context)
            
            for (message in messages) {
                val address = message.originatingAddress ?: continue
                val body = message.messageBody ?: ""
                val date = message.timestampMillis

                // Insert into system SMS database
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, date)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                }
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)

                // Get thread ID for deep linking
                val threadId = repository.getThreadIdForAddress(address) ?: -1L
                
                // Get contact info for richer notification
                val contactInfo = repository.getContactInfo(address)

                // Show notification
                showNotification(context, address, contactInfo, body, date, threadId)
            }
        }
    }

    private fun showNotification(
        context: Context, 
        address: String, 
        contactInfo: SmsRepository.ContactInfo, 
        body: String, 
        date: Long,
        threadId: Long
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("threadId", threadId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, threadId.hashCode(), notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val senderName = contactInfo.name ?: address
        val personBuilder = Person.Builder()
            .setName(senderName)
            .setImportant(true)

        contactInfo.photoUri?.let { uriString ->
            try {
                val inputStream = context.contentResolver.openInputStream(Uri.parse(uriString))
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    personBuilder.setIcon(IconCompat.createWithBitmap(bitmap))
                }
            } catch (e: Exception) {
                // Fallback to no icon if loading fails
            }
        }
        val person = personBuilder.build()

        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .addMessage(body, date, person)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(threadId.toString()) // Useful for conversational notifications
            .build()

        notificationManager.notify(threadId.hashCode(), notification)
    }
}

