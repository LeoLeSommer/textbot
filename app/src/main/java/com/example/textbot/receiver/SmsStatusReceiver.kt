package com.example.textbot.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.textbot.data.repository.SmsRepository

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SmsRepository.SMS_SENT_ACTION) {
            val uri = intent.data ?: return
            val repository = SmsRepository(context)
            
            val statusType = when (resultCode) {
                Activity.RESULT_OK -> Telephony.Sms.MESSAGE_TYPE_SENT
                else -> Telephony.Sms.MESSAGE_TYPE_FAILED
            }
            
            Log.d("SmsStatusReceiver", "Updating status for $uri to $statusType (result: $resultCode)")
            repository.updateMessageStatus(uri, statusType)
        }
    }
}
