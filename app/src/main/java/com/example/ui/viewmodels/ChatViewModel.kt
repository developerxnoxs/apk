package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.models.*
import com.example.data.repository.ChatRepository
import androidx.room.Room
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "telegram_e2ee.db"
    ).fallbackToDestructiveMigration().build()

    val repository = ChatRepository(db, application)

    // Current User
    private val _currentUser = MutableStateFlow<User?>(repository.getCurrentUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Auth Step State
    private val _authStep = MutableStateFlow(
        if (repository.getCurrentUser() != null) AuthStep.LOGGED_IN else AuthStep.PHONE_INPUT
    )
    val authStep: StateFlow<AuthStep> = _authStep.asStateFlow()

    val phoneNumber = MutableStateFlow("+62 ")
    val otpCode = MutableStateFlow("")
    val displayName = MutableStateFlow("")
    val username = MutableStateFlow("")
    val bio = MutableStateFlow("Menggunakan Telegram E2EE 🔐")

    val authError = MutableStateFlow<String?>(null)
    val isAuthLoading = MutableStateFlow(false)

    // Chats and Contacts
    val allChats: StateFlow<List<Chat>> = repository.allChats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allContacts: StateFlow<List<User>> = repository.allContacts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Search query
    val searchQuery = MutableStateFlow("")

    val filteredChats: StateFlow<List<Chat>> = combine(allChats, searchQuery) { chats, query ->
        if (query.isBlank()) chats
        else chats.filter { it.title.contains(query, ignoreCase = true) || it.lastMessageText.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Chat Selection
    private val _activeChat = MutableStateFlow<Chat?>(null)
    val activeChat: StateFlow<Chat?> = _activeChat.asStateFlow()

    // Typing Statuses: Map of Chat ID -> Name of typing user
    val typingStatuses: StateFlow<Map<String, String?>> = repository.typingStatus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Active Chat Typing user name (if any)
    val activeChatTypingUser: StateFlow<String?> = combine(_activeChat, typingStatuses) { chat, map ->
        if (chat == null) null else map[chat.id]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setUserTyping(isTyping: Boolean) {
        val chat = _activeChat.value ?: return
        viewModelScope.launch {
            repository.broadcastUserTyping(chat.id, isTyping)
        }
    }

    // Active Chat Messages
    val activeMessages: StateFlow<List<Message>> = _activeChat.flatMapLatest { chat ->
        if (chat == null) flowOf(emptyList())
        else repository.getMessagesForChat(chat.id, chat.secretKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Auth Actions
    fun requestOtp() {
        val phone = phoneNumber.value.trim()
        if (phone.length < 8) {
            authError.value = "Nomor telepon tidak valid. Masukkan nomor telepon yang benar."
            return
        }
        authError.value = null
        isAuthLoading.value = true

        viewModelScope.launch {
            kotlinx.coroutines.delay(1000) // Simulated OTP dispatch
            isAuthLoading.value = false
            _authStep.value = AuthStep.OTP_VERIFY
        }
    }

    fun verifyOtp() {
        val code = otpCode.value.trim()
        if (code.length != 6) {
            authError.value = "Kode verifikasi OTP harus 6 digit."
            return
        }
        authError.value = null
        isAuthLoading.value = true

        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            isAuthLoading.value = false
            _authStep.value = AuthStep.PROFILE_SETUP
        }
    }

    fun completeProfile() {
        val name = displayName.value.trim()
        if (name.isBlank()) {
            authError.value = "Nama lengkap tidak boleh kosong."
            return
        }

        val userId = "user_${System.currentTimeMillis()}"
        val rawUsername = username.value.trim().removePrefix("@")
        val cleanedUsername = if (rawUsername.isNotBlank()) rawUsername else name.lowercase().replace(" ", "_")
        val user = User(
            id = userId,
            phoneNumber = phoneNumber.value.trim(),
            displayName = name,
            username = cleanedUsername,
            bio = bio.value.trim(),
            avatarUrl = "https://picsum.photos/seed/$userId/200/200"
        )

        repository.saveCurrentUser(user)
        _currentUser.value = user
        _authStep.value = AuthStep.LOGGED_IN
    }

    fun logout() {
        repository.logout()
        _currentUser.value = null
        _authStep.value = AuthStep.PHONE_INPUT
    }

    // Editing Message State
    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage.asStateFlow()

    fun setEditingMessage(message: Message?) {
        _editingMessage.value = message
    }

    fun saveEditedMessage(messageId: String, newText: String) {
        val chat = _activeChat.value ?: return
        if (newText.isBlank()) return
        viewModelScope.launch {
            repository.editMessage(
                messageId = messageId,
                chatId = chat.id,
                secretKey = chat.secretKey,
                newText = newText
            )
            _editingMessage.value = null
        }
    }

    fun setActiveChat(chat: Chat) {
        _activeChat.value = chat
    }

    fun sendMessage(
        text: String,
        mediaType: MediaType = MediaType.TEXT,
        mediaUrl: String = "",
        mediaName: String = "",
        mediaSize: String = ""
    ) {
        val chat = _activeChat.value ?: return
        if (text.isBlank() && mediaUrl.isBlank() && mediaType == MediaType.TEXT) return

        viewModelScope.launch {
            repository.sendMessage(
                chatId = chat.id,
                secretKey = chat.secretKey,
                text = text,
                mediaType = mediaType,
                mediaUrl = mediaUrl,
                mediaName = mediaName,
                mediaSize = mediaSize
            )
        }
    }

    fun startChatWithContact(contact: User, isSecret: Boolean = true, onCreated: (Chat) -> Unit) {
        viewModelScope.launch {
            val chat = repository.createChat(contact, isSecret)
            _activeChat.value = chat
            onCreated(chat)
        }
    }
}

enum class AuthStep {
    PHONE_INPUT, OTP_VERIFY, PROFILE_SETUP, LOGGED_IN
}
