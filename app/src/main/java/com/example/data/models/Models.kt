package com.example.data.models

enum class MediaType {
    TEXT, IMAGE, VOICE, FILE, LOCATION
}

enum class MessageStatus {
    PENDING, SENT, DELIVERED, READ
}

data class User(
    val id: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "Menggunakan Telegram E2EE",
    val avatarUrl: String = "",
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)

data class Chat(
    val id: String = "",
    val title: String = "",
    val isSecret: Boolean = true,
    val secretKey: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val avatarUrl: String = ""
)

data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val cipherText: String = "",
    val iv: String = "",
    val isEncrypted: Boolean = true,
    val mediaType: MediaType = MediaType.TEXT,
    val mediaUrl: String = "",
    val mediaName: String = "",
    val mediaSize: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val reaction: String = "",
    val isEdited: Boolean = false
)
