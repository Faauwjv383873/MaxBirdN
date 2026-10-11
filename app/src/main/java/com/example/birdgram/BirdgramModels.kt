package com.example.birdgram

import androidx.compose.runtime.Immutable

/**
 * Type of conversation in Birdgram
 */
enum class BirdgramChatType {
    DIRECT,        // 1-on-1 private student chat
    STUDY_GROUP,   // Subject/Topic discussion group
    CHANNEL        // Broadcast/Notes channel
}

/**
 * Message delivery status
 */
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
}

/**
 * Represents a conversation in Birdgram
 */
@Immutable
data class BirdgramChat(
    val id: String,
    val title: String,
    val type: BirdgramChatType,
    val avatarUrl: String? = null,
    val avatarInitial: String = "B",
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val memberCount: Int = 1,
    val subjectTag: String? = null,
    val isPinned: Boolean = false,
    val isVerified: Boolean = false,
    val telegramUsername: String? = null
)

/**
 * Individual chat message in Birdgram
 */
@Immutable
data class BirdgramMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarUrl: String? = null,
    val text: String = "",
    val imageUrl: String? = null,
    val timestamp: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false,
    val status: MessageStatus = MessageStatus.READ,
    val replyToText: String? = null,
    val isSubjectDoubt: Boolean = false
)

/**
 * Telegram Account Connection Status
 */
sealed class TelegramAuthStatus {
    object Disconnected : TelegramAuthStatus()
    data class CodeRequested(val phoneNumber: String, val phoneCodeHash: String) : TelegramAuthStatus()
    data class Connected(
        val phoneNumber: String,
        val firstName: String,
        val username: String?,
        val apiId: String = "38634154"
    ) : TelegramAuthStatus()
}
