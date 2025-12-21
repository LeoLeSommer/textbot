package com.example.textbot.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            // TODO: Process the incoming SMS and save it to the database
            // For now, we just acknowledge receiving it so the system knows we handled it.
        }
    }
}
