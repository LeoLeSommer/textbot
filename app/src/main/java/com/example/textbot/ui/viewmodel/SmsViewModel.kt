package com.example.textbot.ui.viewmodel

import android.util.Log

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.textbot.data.model.Conversation
import com.example.textbot.data.model.SmsMessage
import com.example.textbot.data.repository.SmsRepository
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SmsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmsRepository(application)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var currentThreadId: Long? = null

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            // Refresh conversations list
            loadConversations()
            // Refresh current conversation detail if visible
            currentThreadId?.let {
                markAsRead(it)
                loadMessages(it)
            }
        }
    }

    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            loadConversations()
        }
    }

    init {
        application.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver
        )
        application.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            contactsObserver
        )
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(smsObserver)
        getApplication<Application>().contentResolver.unregisterContentObserver(contactsObserver)
    }

    fun setCurrentThreadId(threadId: Long?) {
        currentThreadId = threadId
    }

    fun loadConversations() {
        viewModelScope.launch {
            _loading.value = true
            try {
                Log.d("SmsViewModel", "Loading conversations...")
                val conversations = repository.getAllConversations()
                Log.d("SmsViewModel", "Loaded ${conversations.size} conversations")
                _conversations.value = conversations
            } catch (e: Exception) {
                Log.e("SmsViewModel", "Error loading conversations", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun markAsRead(threadId: Long) {
        viewModelScope.launch {
            try {
                repository.markAsRead(threadId)
                // Explicitly refresh conversations to update unread counts immediately
                loadConversations()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun loadMessages(threadId: Long) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _messages.value = repository.getMessagesForThread(threadId)
            } catch (e: Exception) {
                // Handle error
            } finally {
                _loading.value = false
            }
        }
    }

    fun sendMessage(address: String, body: String) {
        sendMessageWithAttachments(address, body, emptyList())
    }

    fun sendMessageWithAttachments(address: String, body: String, attachments: List<com.example.textbot.data.model.Attachment>) {
        viewModelScope.launch {
            try {
                if (attachments.isEmpty()) {
                    repository.sendMessage(address, body)
                } else {
                    repository.sendMms(address, body, attachments)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun getOrCreateThreadId(phoneNumber: String): Long? {
        return repository.getThreadIdForAddress(phoneNumber)
    }
}
