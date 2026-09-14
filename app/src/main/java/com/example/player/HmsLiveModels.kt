package com.example.player

import java.util.UUID

/**
 * Data models representing real-time interactive states for 100ms Live Class WebSocket stream.
 */

data class PinnedMessageData(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val pinnedBy: String = "শিক্ষক / মডারেটর",
    val pinnedAt: Long = System.currentTimeMillis()
)

data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val senderName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false,
    val recipientRole: String? = "teacher"
)

data class PollOption(
    val id: String,
    val text: String,
    val voteCount: Int = 0
)

data class PollData(
    val id: String,
    val title: String,
    val question: String,
    val options: List<PollOption>,
    val durationSeconds: Int = 30,
    val selectedOptionId: String? = null
)

enum class LiveChatRecipient(val role: String, val labelBangla: String) {
    TEACHER("teacher", "To teacher"),
    EVERYONE("everyone", "To everyone")
}
