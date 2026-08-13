package com.example.data.repository

import android.content.Context
import com.example.crypto.CryptoUtils
import com.example.data.local.AppDatabase
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import com.example.data.models.*
import com.example.data.remote.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatRepository(
    private val db: AppDatabase,
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        NotificationHelper.createNotificationChannel(context)
        scope.launch {
            seedDefaultDataIfNeeded()
        }
    }

    // Local Current User Cache in SharedPreferences
    private val prefs = context.getSharedPreferences("telegram_e2ee_prefs", Context.MODE_PRIVATE)

    fun getCurrentUser(): User? {
        val id = prefs.getString("user_id", null) ?: return null
        val phone = prefs.getString("user_phone", "") ?: ""
        val name = prefs.getString("user_name", "") ?: ""
        val username = (prefs.getString("user_username", "") ?: "").trim().removePrefix("@")
        val bio = prefs.getString("user_bio", "Menggunakan Telegram E2EE") ?: ""
        val avatar = prefs.getString("user_avatar", "") ?: ""
        return User(
            id = id,
            phoneNumber = phone,
            displayName = name,
            username = username,
            bio = bio,
            avatarUrl = avatar
        )
    }

    fun saveCurrentUser(user: User) {
        val sanitizedUsername = user.username.trim().removePrefix("@")
        prefs.edit().apply {
            putString("user_id", user.id)
            putString("user_phone", user.phoneNumber)
            putString("user_name", user.displayName)
            putString("user_username", sanitizedUsername)
            putString("user_bio", user.bio)
            putString("user_avatar", user.avatarUrl)
            apply()
        }
        scope.launch {
            db.userDao().insertUser(
                UserEntity(
                    id = user.id,
                    phoneNumber = user.phoneNumber,
                    displayName = user.displayName,
                    username = sanitizedUsername,
                    bio = user.bio,
                    avatarUrl = user.avatarUrl,
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
            )
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    // Reactive Chat Flow
    val allChats: Flow<List<Chat>> = db.chatDao().getAllChats().map { entities ->
        entities.map { entity ->
            Chat(
                id = entity.id,
                title = entity.title,
                isSecret = entity.isSecret,
                secretKey = entity.secretKey,
                participantIds = entity.participantIdsCsv.split(",").filter { it.isNotBlank() },
                lastMessageText = entity.lastMessageText,
                lastMessageTime = entity.lastMessageTime,
                unreadCount = entity.unreadCount,
                avatarUrl = entity.avatarUrl
            )
        }
    }

    // Reactive Contacts Flow
    val allContacts: Flow<List<User>> = db.userDao().getAllUsers().map { entities ->
        entities.map { entity ->
            User(
                id = entity.id,
                phoneNumber = entity.phoneNumber,
                displayName = entity.displayName,
                username = entity.username,
                bio = entity.bio,
                avatarUrl = entity.avatarUrl,
                isOnline = entity.isOnline,
                lastSeen = entity.lastSeen
            )
        }
    }

    // Typing status map: Chat ID -> Name of person typing or null
    private val _typingStatus = MutableStateFlow<Map<String, String?>>(emptyMap())
    val typingStatus: StateFlow<Map<String, String?>> = _typingStatus.asStateFlow()

    fun setTyping(chatId: String, typingUser: String?) {
        val current = _typingStatus.value.toMutableMap()
        if (typingUser == null) {
            current.remove(chatId)
        } else {
            current[chatId] = typingUser
        }
        _typingStatus.value = current
    }

    suspend fun broadcastUserTyping(chatId: String, isTyping: Boolean) = withContext(Dispatchers.IO) {
        // Typing status updated locally
    }
    fun getMessagesForChat(chatId: String, secretKey: String): Flow<List<Message>> {
        return db.messageDao().getMessagesForChat(chatId).map { entities ->
            entities.map { entity ->
                val plainText = if (entity.isEncrypted && entity.cipherText.isNotBlank()) {
                    CryptoUtils.decrypt(entity.cipherText, entity.iv, secretKey)
                } else {
                    entity.cipherText
                }

                Message(
                    id = entity.id,
                    chatId = entity.chatId,
                    senderId = entity.senderId,
                    senderName = entity.senderName,
                    cipherText = plainText, // decrypted string for UI display
                    iv = entity.iv,
                    isEncrypted = entity.isEncrypted,
                    mediaType = try { MediaType.valueOf(entity.mediaType) } catch (_: Exception) { MediaType.TEXT },
                    mediaUrl = entity.mediaUrl,
                    mediaName = entity.mediaName,
                    mediaSize = entity.mediaSize,
                    timestamp = entity.timestamp,
                    status = try { MessageStatus.valueOf(entity.status) } catch (_: Exception) { MessageStatus.SENT },
                    isEdited = entity.isEdited
                )
            }
        }
    }

    // Edit an existing message
    suspend fun editMessage(
        messageId: String,
        chatId: String,
        secretKey: String,
        newText: String
    ) = withContext(Dispatchers.IO) {
        val existing = db.messageDao().getMessageById(messageId) ?: return@withContext
        val encryptedPayload = CryptoUtils.encrypt(newText, secretKey)

        val updatedEntity = existing.copy(
            cipherText = encryptedPayload.cipherText,
            iv = encryptedPayload.iv,
            isEdited = true
        )
        db.messageDao().insertMessage(updatedEntity)

        // Update last message in chat preview if this was the latest message
        val existingChat = db.chatDao().getChatById(chatId)
        if (existingChat != null) {
            db.chatDao().insertChat(
                existingChat.copy(
                    lastMessageText = "🔐 $newText (diedit)"
                )
            )
        }
    }

    // Send E2EE Encrypted Message
    suspend fun sendMessage(
        chatId: String,
        secretKey: String,
        text: String,
        mediaType: MediaType = MediaType.TEXT,
        mediaUrl: String = "",
        mediaName: String = "",
        mediaSize: String = ""
    ) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        val encryptedPayload = CryptoUtils.encrypt(text, secretKey)
        val messageId = "msg_${System.currentTimeMillis()}"

        val messageEntity = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = currentUser.id,
            senderName = currentUser.displayName,
            cipherText = encryptedPayload.cipherText,
            iv = encryptedPayload.iv,
            isEncrypted = true,
            mediaType = mediaType.name,
            mediaUrl = mediaUrl,
            mediaName = mediaName,
            mediaSize = mediaSize,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT.name
        )

        // Save locally to Room
        db.messageDao().insertMessage(messageEntity)

        // Update Chat metadata
        val previewText = when (mediaType) {
            MediaType.TEXT -> text
            MediaType.IMAGE -> "📷 Foto terenkripsi"
            MediaType.VOICE -> "🎤 Pesan suara (${mediaSize.ifBlank { "0:15" }})"
            MediaType.FILE -> "📄 Dokumen: $mediaName"
            MediaType.LOCATION -> "📍 Lokasi terkini"
        }

        val existingChat = db.chatDao().getChatById(chatId)
        if (existingChat != null) {
            db.chatDao().insertChat(
                existingChat.copy(
                    lastMessageText = "🔐 $previewText",
                    lastMessageTime = System.currentTimeMillis()
                )
            )
        }

        // Simulate instant AI / contact response for interactive rich experience if chatting with bot/contacts
        simulateContactReply(chatId, secretKey, text)
    }

    private fun simulateContactReply(chatId: String, secretKey: String, userPrompt: String) {
        val chatEntity = scope.launch {
            val chat = db.chatDao().getChatById(chatId) ?: return@launch
            if (chat.title.contains("Saved Messages", ignoreCase = true)) return@launch // Don't reply to self

            kotlinx.coroutines.delay(600)
            // Activate typing indicator
            setTyping(chatId, chat.title)

            kotlinx.coroutines.delay(1800) // Realistic typing duration
            setTyping(chatId, null) // Clear typing indicator right before message delivers

            val replyText = when {
                userPrompt.contains("halo", ignoreCase = true) || userPrompt.contains("hi", ignoreCase = true) ->
                    "Halo! Pesan Anda telah diterima secara aman melalui enkripsi End-to-End (AES-256-GCM) Telegram E2EE. 🔐"
                userPrompt.contains("gambar", ignoreCase = true) || userPrompt.contains("foto", ignoreCase = true) ->
                    "Siap, gambar diterima dengan kunci enkripsi privat yang cocok! 📷"
                userPrompt.contains("kunci", ignoreCase = true) || userPrompt.contains("key", ignoreCase = true) ->
                    "Kunci enkripsi rahasia kita terverifikasi sempurna. Sifat komunikasi ini 100% privat. 🛡️"
                else ->
                    "Pesan terenkripsi diterima: \"$userPrompt\". Kode Hash Fingerprint: ${CryptoUtils.generateEmojiFingerprint(secretKey)}"
            }

            val enc = CryptoUtils.encrypt(replyText, secretKey)
            val replyId = "reply_${System.currentTimeMillis()}"

            val replyMsg = MessageEntity(
                id = replyId,
                chatId = chatId,
                senderId = "contact_${chat.id}",
                senderName = chat.title,
                cipherText = enc.cipherText,
                iv = enc.iv,
                isEncrypted = true,
                mediaType = MediaType.TEXT.name,
                mediaUrl = "",
                mediaName = "",
                mediaSize = "",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.DELIVERED.name
            )

            db.messageDao().insertMessage(replyMsg)
            db.chatDao().insertChat(
                chat.copy(
                    lastMessageText = "🔐 $replyText",
                    lastMessageTime = System.currentTimeMillis()
                )
            )

            NotificationHelper.showPrivateMessageNotification(
                context = context,
                senderName = chat.title,
                decryptedText = "🔐 $replyText",
                chatId = chatId
            )
        }
    }

    // Create New Secret Chat or Regular Chat
    suspend fun createChat(contact: User, isSecret: Boolean = true): Chat = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: User(id = "user_me", displayName = "Saya")
        val chatId = "chat_${listOf(currentUser.id, contact.id).sorted().joinToString("_")}"

        val sharedKey = CryptoUtils.deriveSharedKey(currentUser.id, contact.id)

        val chatEntity = ChatEntity(
            id = chatId,
            title = contact.displayName,
            isSecret = isSecret,
            secretKey = sharedKey,
            participantIdsCsv = "${currentUser.id},${contact.id}",
            lastMessageText = if (isSecret) "🔐 Obrolan Rahasia E2EE Dimulai" else "Obrolan Dimulai",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0,
            avatarUrl = contact.avatarUrl
        )

        db.chatDao().insertChat(chatEntity)

        // Seed initial greeting message
        val greetingText = if (isSecret) {
            "Selamat datang di Obrolan Rahasia Terenkripsi End-to-End! Pesan dilapisi enkripsi AES-256-GCM. Kunci Fingerprint: ${CryptoUtils.generateEmojiFingerprint(sharedKey)}"
        } else {
            "Halo ${contact.displayName}! Mari berkomunikasi dengan aman."
        }

        val encGreeting = CryptoUtils.encrypt(greetingText, sharedKey)
        db.messageDao().insertMessage(
            MessageEntity(
                id = "init_$chatId",
                chatId = chatId,
                senderId = contact.id,
                senderName = contact.displayName,
                cipherText = encGreeting.cipherText,
                iv = encGreeting.iv,
                isEncrypted = isSecret,
                mediaType = MediaType.TEXT.name,
                mediaUrl = "",
                mediaName = "",
                mediaSize = "",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.READ.name
            )
        )

        Chat(
            id = chatId,
            title = contact.displayName,
            isSecret = isSecret,
            secretKey = sharedKey,
            participantIds = listOf(currentUser.id, contact.id),
            lastMessageText = "🔐 $greetingText",
            lastMessageTime = System.currentTimeMillis(),
            avatarUrl = contact.avatarUrl
        )
    }

    private suspend fun seedDefaultDataIfNeeded() {
        val count = db.userDao().getAllUsers().firstOrNull()?.size ?: 0
        if (count == 0) {
            val seedContacts = listOf(
                UserEntity(
                    id = "pavel_durov",
                    phoneNumber = "+1 202 555 0199",
                    displayName = "Pavel Durov",
                    username = "durov",
                    bio = "Building Telegram for freedom and privacy.",
                    avatarUrl = "https://picsum.photos/seed/durov/200/200",
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                ),
                UserEntity(
                    id = "telegram_bot",
                    phoneNumber = "+62 812 0000 1111",
                    displayName = "Telegram Security Bot",
                    username = "security_bot",
                    bio = "Verifikasi Kunci E2EE dan Notifikasi Keamanan Telegram.",
                    avatarUrl = "https://picsum.photos/seed/secbot/200/200",
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                ),
                UserEntity(
                    id = "siti_rahma",
                    phoneNumber = "+62 812 3456 7890",
                    displayName = "Siti Rahma",
                    username = "siti_rahma",
                    bio = "Product Designer | Crypto Enthusiast",
                    avatarUrl = "https://picsum.photos/seed/siti/200/200",
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                ),
                UserEntity(
                    id = "ahmad_fauzi",
                    phoneNumber = "+62 857 9999 8888",
                    displayName = "Ahmad Fauzi",
                    username = "fauzi_dev",
                    bio = "Android Engineer & Kotlin Dev",
                    avatarUrl = "https://picsum.photos/seed/fauzi/200/200",
                    isOnline = false,
                    lastSeen = System.currentTimeMillis() - 3600000
                )
            )

            db.userDao().insertUsers(seedContacts)

            // Auto-create a default chat with Pavel Durov & Telegram Security Bot
            val defaultUser = getCurrentUser() ?: User(id = "user_me", displayName = "Pengguna Telegram")
            val pavelKey = CryptoUtils.deriveSharedKey(defaultUser.id, "pavel_durov")
            val pavelChatId = "chat_${listOf(defaultUser.id, "pavel_durov").sorted().joinToString("_")}"

            db.chatDao().insertChat(
                ChatEntity(
                    id = pavelChatId,
                    title = "Pavel Durov 🔐",
                    isSecret = true,
                    secretKey = pavelKey,
                    participantIdsCsv = "${defaultUser.id},pavel_durov",
                    lastMessageText = "🔐 Welcome to Telegram End-to-End Encryption!",
                    lastMessageTime = System.currentTimeMillis(),
                    unreadCount = 1,
                    avatarUrl = "https://picsum.photos/seed/durov/200/200"
                )
            )

            val encMsg = CryptoUtils.encrypt(
                "Welcome to Telegram E2EE! All your messages, media files, and voice notes are secured with instant client-side end-to-end encryption. Enjoy private communication!",
                pavelKey
            )

            db.messageDao().insertMessage(
                MessageEntity(
                    id = "welcome_msg",
                    chatId = pavelChatId,
                    senderId = "pavel_durov",
                    senderName = "Pavel Durov",
                    cipherText = encMsg.cipherText,
                    iv = encMsg.iv,
                    isEncrypted = true,
                    mediaType = MediaType.TEXT.name,
                    mediaUrl = "",
                    mediaName = "",
                    mediaSize = "",
                    timestamp = System.currentTimeMillis() - 60000,
                    status = MessageStatus.READ.name
                )
            )
        }
    }
}
