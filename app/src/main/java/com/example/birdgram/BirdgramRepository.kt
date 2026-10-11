package com.example.birdgram

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.example.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Birdgram Repository - Real Telegram Integration Manager.
 *
 * Connects directly to real Telegram servers via Telegram's official web clients (Web K / Web A).
 * - Real phone number login & real SMS / Telegram app verification codes.
 * - Real Telegram chat lists, study groups, channels, direct messages.
 * - Real media sharing, document downloads, audio playback.
 * - Persistent session storage with encrypted SQLite/IndexedDB & cookie persistence.
 */
class BirdgramRepository(
    private val context: Context,
    private val sessionManager: SessionManager? = null
) {
    companion object {
        const val TELEGRAM_API_ID = "38634154"
        const val TELEGRAM_API_HASH = "a4194e506db4ad97f1dba20ec28ac383"
        private const val PREFS_NAME = "birdgram_prefs"
        private const val KEY_VARIANT = "tg_client_variant"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _sessionInfo = MutableStateFlow(loadInitialSession())
    val sessionInfo: StateFlow<TelegramSessionInfo> = _sessionInfo.asStateFlow()

    private fun loadInitialSession(): TelegramSessionInfo {
        val savedVariantName = prefs.getString(KEY_VARIANT, TelegramClientVariant.WEB_K.name)
        val variant = try {
            TelegramClientVariant.valueOf(savedVariantName ?: TelegramClientVariant.WEB_K.name)
        } catch (_: Exception) {
            TelegramClientVariant.WEB_K
        }
        return TelegramSessionInfo(
            selectedVariant = variant,
            isConnected = true,
            lastActiveUrl = variant.url
        )
    }

    fun setClientVariant(variant: TelegramClientVariant) {
        prefs.edit().putString(KEY_VARIANT, variant.name).apply()
        _sessionInfo.update {
            it.copy(
                selectedVariant = variant,
                lastActiveUrl = variant.url
            )
        }
    }

    /**
     * Clear all cached Telegram session data (cookies, LocalStorage, IndexedDB)
     * if the user desires a complete fresh login.
     */
    fun clearTelegramSession(onCleared: () -> Unit = {}) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies {
                cookieManager.flush()
                WebStorage.getInstance().deleteAllData()
                onCleared()
            }
        } catch (_: Exception) {
            onCleared()
        }
    }
}
