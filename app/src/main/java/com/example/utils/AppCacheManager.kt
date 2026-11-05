package com.example.utils

import android.content.Context
import android.util.Log
import com.example.auth.SessionManager
import com.example.course.CourseCommerceRepository
import com.example.database.AppDatabase
import com.example.notification.ClassAlarmScheduler
import com.example.player.ShikhoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class CacheBreakdown(
    val mediaStreamBytes: Long,
    val pdfPreviewBytes: Long,
    val imageAndNetworkBytes: Long,
    val totalBytes: Long
) {
    fun formatSize(bytes: Long): String = AppCacheManager.formatBytes(bytes)
}

object AppCacheManager {
    private const val TAG = "AppCacheManager"

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes.toFloat() / (1024f * 1024f))
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes.toFloat() / 1024f)
            else -> "0.0 MB"
        }
    }

    /**
     * Returns a category breakdown of all temporary caches on the device.
     */
    fun getCacheBreakdown(context: Context): CacheBreakdown {
        var mediaBytes = 0L
        var pdfBytes = 0L
        var otherBytes = 0L

        try {
            val dirs = listOfNotNull(context.cacheDir, context.externalCacheDir)
            for (root in dirs) {
                root.listFiles()?.forEach { child ->
                    val size = getFolderSize(child)
                    val name = child.name.lowercase(Locale.US)
                    when {
                        name.contains("media") || name.contains("exo") || name.contains("video") ->
                            mediaBytes += size
                        name.contains("pdf") || name.contains("slide") || name.contains("doc") ->
                            pdfBytes += size
                        else ->
                            otherBytes += size
                    }
                }
            }
            context.codeCacheDir?.let { otherBytes += getFolderSize(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing cache breakdown: ${e.message}")
        }

        val total = mediaBytes + pdfBytes + otherBytes
        return CacheBreakdown(
            mediaStreamBytes = mediaBytes,
            pdfPreviewBytes = pdfBytes,
            imageAndNetworkBytes = otherBytes,
            totalBytes = total
        )
    }

    /**
     * Calculates the total size of all cache directories in bytes.
     */
    fun getTotalCacheSizeBytes(context: Context): Long {
        var totalBytes = 0L
        try {
            context.cacheDir?.let { totalBytes += getFolderSize(it) }
            context.externalCacheDir?.let { totalBytes += getFolderSize(it) }
            context.codeCacheDir?.let { totalBytes += getFolderSize(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size: ${e.message}")
        }
        return totalBytes
    }

    /**
     * Formats total cache size in human-friendly string (e.g. "12.4 MB" or "0.0 MB").
     */
    fun getFormattedCacheSize(context: Context): String {
        return formatBytes(getTotalCacheSizeBytes(context))
    }

    /**
     * Completely purges all app caches:
     * - ExoPlayer media stream cache
     * - In-app PDF preview cache
     * - Image/Network caches in cacheDir, externalCacheDir, and codeCacheDir
     * (Preserves offline downloads stored in filesDir/offline_downloads)
     */
    fun clearAllAppCache(context: Context): Boolean {
        return try {
            // 0. Wipe any downloaded APK update files
            try {
                com.example.update.InAppApkUpdater.clearAllDownloadedApks(context)
            } catch (_: Throwable) {}

            // 1. Release ExoPlayer SimpleCache first
            ShikhoPlayerManager.clearMediaCache(context)

            // 2. Wipe standard cacheDir contents
            context.cacheDir?.let { dir ->
                dir.listFiles()?.forEach { file ->
                    try {
                        file.deleteRecursively()
                    } catch (_: Exception) {}
                }
            }

            // 3. Wipe externalCacheDir if present
            context.externalCacheDir?.let { dir ->
                dir.listFiles()?.forEach { file ->
                    try {
                        file.deleteRecursively()
                    } catch (_: Exception) {}
                }
            }

            // 4. Wipe codeCacheDir temporary files if present
            context.codeCacheDir?.let { dir ->
                dir.listFiles()?.forEach { file ->
                    try {
                        file.deleteRecursively()
                    } catch (_: Exception) {}
                }
            }

            Log.i(TAG, "✅ All app caches cleared successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing app cache: ${e.message}")
            false
        }
    }

    /**
     * Performs a complete, deep wipe of the user's account data, Room database user tables,
     * session tokens, scheduled alarms, in-memory caches, and disk caches when logging out.
     */
    suspend fun wipeAllAccountAndCacheOnLogout(context: Context) = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        // 1. Clear session & account SharedPreferences
        try {
            SessionManager(appCtx).clearSession()
        } catch (e: Throwable) {
            Log.w(TAG, "Session clear error: ${e.message}")
        }

        // 2. Clear auxiliary account caches in SharedPreferences
        val auxPrefs = listOf(
            "shikho_course_commerce_cache",
            "lesson_progress_prefs",
            "routine_cache_prefs",
            "exam_cache_prefs"
        )
        for (prefName in auxPrefs) {
            try {
                appCtx.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (_: Throwable) {}
        }

        // 3. Clear all Room database tables (saved items, completed items, video progress, model tests, leaderboard, etc.)
        try {
            val db = AppDatabase.getDatabase(appCtx)
            db.clearAllTables()
        } catch (e: Throwable) {
            Log.w(TAG, "Room user table wipe error: ${e.message}")
        }

        // 4. Clear in-memory caches & alarms
        try {
            CourseCommerceRepository.clearCache(appCtx)
        } catch (_: Throwable) {}
        try {
            ClassAlarmScheduler.cancelAllAlarms(appCtx)
        } catch (_: Throwable) {}

        // 5. Purge all disk & media caches from the phone
        clearAllAppCache(appCtx)
    }

    /**
     * Auto-trims cache if it grows beyond a reasonable threshold (250MB),
     * while preserving ExoPlayer media cache and PDF preview cache.
     * Always purges installed or stale APK files first.
     */
    fun autoTrimExcessiveCache(context: Context) {
        try {
            com.example.update.InAppApkUpdater.cleanupInstalledOrStaleApks(context)
            val totalBytes = getTotalCacheSizeBytes(context)
            if (totalBytes > 250L * 1024L * 1024L) {
                Log.w(TAG, "Cache size exceeded threshold (${totalBytes / (1024 * 1024)}MB). Auto-trimming non-essential temporary items...")
                val protectedNames = setOf("shikho_media_cache", "pdf_preview_cache", "downloads")
                context.cacheDir?.listFiles()?.forEach { file ->
                    if (!protectedNames.contains(file.name)) {
                        try { file.deleteRecursively() } catch (_: Exception) {}
                    }
                }
                context.externalCacheDir?.listFiles()?.forEach { file ->
                    if (!protectedNames.contains(file.name)) {
                        try { file.deleteRecursively() } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-trim cache error: ${e.message}")
        }
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            dir.walkTopDown().filter { it.isFile }.forEach {
                size += it.length()
            }
        } catch (_: Exception) {}
        return size
    }
}
