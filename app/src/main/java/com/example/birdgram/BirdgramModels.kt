package com.example.birdgram

import androidx.compose.runtime.Immutable

/**
 * Official Telegram Web client variant.
 * Telegram provides two modern web clients:
 * - WEB_K (https://web.telegram.org/k/) - Highly optimized for mobile touch devices
 * - WEB_A (https://web.telegram.org/a/) - Modern full-featured WebZ client
 */
enum class TelegramClientVariant(val displayName: String, val url: String) {
    WEB_K("Telegram Web K", "https://web.telegram.org/k/"),
    WEB_A("Telegram Web A", "https://web.telegram.org/a/")
}

/**
 * Real Telegram connection state
 */
@Immutable
data class TelegramSessionInfo(
    val selectedVariant: TelegramClientVariant = TelegramClientVariant.WEB_K,
    val isConnected: Boolean = true,
    val lastActiveUrl: String = "https://web.telegram.org/k/"
)
