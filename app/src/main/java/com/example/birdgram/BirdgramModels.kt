package com.example.birdgram

import androidx.compose.runtime.Immutable

/**
 * Official Telegram Web client variant.
 * Telegram provides two modern web clients:
 * - WEB_K (https://web.telegram.org/k/) - Optimized for mobile touch devices
 * - WEB_A (https://web.telegram.org/a/) - Modern full-featured WebZ client
 */
enum class TelegramClientVariant(val displayName: String, val url: String) {
    WEB_K("Telegram Web K", "https://web.telegram.org/k/"),
    WEB_A("Telegram Web A", "https://web.telegram.org/a/")
}

/**
 * Cloud and Local synchronization state for the Telegram session
 */
sealed class TelegramSyncState {
    object Idle : TelegramSyncState()
    object Syncing : TelegramSyncState()
    data class Synced(val lastSyncedTime: String) : TelegramSyncState()
    data class Restored(val message: String) : TelegramSyncState()
    data class Error(val message: String) : TelegramSyncState()
}

/**
 * Encapsulates the entire Telegram session state linked to the student's account access
 */
@Immutable
data class UserTelegramCloudSession(
    val accessKey: String,
    val studentName: String = "",
    val userPhone: String = "",
    val cookiesData: String = "",
    val localStorageData: String = "",
    val sessionVariant: String = "WEB_K",
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Real Telegram connection and UI state
 */
@Immutable
data class TelegramSessionInfo(
    val selectedVariant: TelegramClientVariant = TelegramClientVariant.WEB_K,
    val isConnected: Boolean = true,
    val lastActiveUrl: String = "https://web.telegram.org/k/",
    val syncState: TelegramSyncState = TelegramSyncState.Idle,
    val activeAccessKey: String = "",
    val isRestoredFromCloud: Boolean = false
)
