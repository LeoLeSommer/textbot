package com.example.textbot.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op receiver required for default SMS app eligibility
    }
}
