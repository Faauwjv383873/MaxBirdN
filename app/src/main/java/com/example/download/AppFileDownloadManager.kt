package com.example.download

import android.content.Context
import android.util.Log
import com.example.database.AppDatabase
import com.example.database.DownloadedItemDao
import com.example.database.DownloadedItemEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadQualityOption(
    val id: String, // "720p", "480p", "360p"
    val labelBangla: String,
    val descriptionBangla: String,
    val estimatedSizeBangla: String,
    val targetM3u8Url: String,
    val isRecommended: Boolean = false
)

class AppFileDownloadManager private constructor(
    private val context: Context,
    private val downloadedItemDao: DownloadedItemDao
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val downloadVaultDir: File by lazy {
        File(context.filesDir, "secured_media_vault").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    companion object {
        private const val TAG = "AppFileDownloadManager"
        private const val BUFFER_SIZE = 16384 // 16KB buffer

        @Volatile
        private var INSTANCE: AppFileDownloadManager? = null

        fun getInstance(context: Context): AppFileDownloadManager {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val instance = AppFileDownloadManager(
                    context = context.applicationContext,
                    downloadedItemDao = db.downloadedItemDao()
                )
                INSTANCE = instance
                instance
            }
        }

        /**
         * Resolves the target resolution M3U8 URL given any Shikho master or variant URL.
         */
        fun resolveQualityUrl(baseUrl: String, quality: String): String {
            if (baseUrl.isBlank()) return baseUrl

            // If URL is like https://shikho-stream2.tenbytecdn.com/{id}/playlist.m3u8
            if (baseUrl.contains("/playlist.m3u8")) {
                return baseUrl.replace("/playlist.m3u8", "/$quality/video.m3u8")
            }

            // If URL is already like https://.../{id}/360p/video.m3u8 or 480p/video.m3u8 or 720p/video.m3u8
            if (baseUrl.contains(Regex("/(360p|480p|720p|1080p)/video\\.m3u8"))) {
                return baseUrl.replace(Regex("/(360p|480p|720p|1080p)/video\\.m3u8"), "/$quality/video.m3u8")
            }

            // If URL has stream_0, stream_1, stream_2, stream_3 pattern
            if (baseUrl.contains(Regex("/stream_\\d+/stream\\.m3u8"))) {
                val streamIndex = when (quality) {
                    "1080p" -> "stream_0"
                    "720p" -> "stream_1"
                    "480p" -> "stream_2"
                    "360p" -> "stream_3"
                    else -> "stream_2"
                }
                return baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/$streamIndex/stream.m3u8")
            }

            return baseUrl
        }

        /**
         * Returns download quality options available for the video URL.
         */
        fun getAvailableDownloadQualities(inputUrl: String): List<DownloadQualityOption> {
            val url720 = resolveQualityUrl(inputUrl, "720p")
            val url480 = resolveQualityUrl(inputUrl, "480p")
            val url360 = resolveQualityUrl(inputUrl, "360p")

            return listOf(
                DownloadQualityOption(
                    id = "720p",
                    labelBangla = "720p (এইচডি)",
                    descriptionBangla = "উচ্চ মান ও সবচেয়ে স্পষ্ট ভিডিও",
                    estimatedSizeBangla = "~১২০ - ২৫০ মেগাবাইট",
                    targetM3u8Url = url720,
                    isRecommended = false
                ),
                DownloadQualityOption(
                    id = "480p",
                    labelBangla = "480p (মাঝারি - সেরা পছন্দ)",
                    descriptionBangla = "সাশ্রয়ী ইন্টারনেট ও স্পষ্ট ভিডিও",
                    estimatedSizeBangla = "~৬০ - ১২০ মেগাবাইট",
                    targetM3u8Url = url480,
                    isRecommended = true
                ),
                DownloadQualityOption(
                    id = "360p",
                    labelBangla = "360p (কম ডাটা)",
                    descriptionBangla = "দ্রুত ডাউনলোড ও কম ডাটা খরচ",
                    estimatedSizeBangla = "~৩০ - ৬০ মেগাবাইট",
                    targetM3u8Url = url360,
                    isRecommended = false
                )
            )
        }
    }

    /**
     * Start downloading a file (HLS Video or PDF) into the secured internal vault directory.
     */
    fun downloadFile(
        id: String,
        title: String,
        subtitle: String? = null,
        fileType: String,
        remoteUrl: String
    ): Job {
        // Cancel existing job if running
        activeJobs[id]?.cancel()

        val job = coroutineScope.launch {
            if (remoteUrl.isBlank()) {
                Log.e(TAG, "Download URL is empty for id: $id")
                return@launch
            }

            // Sanitize filename
            val extension = if (fileType.equals(DownloadedItemEntity.FILE_TYPE_PDF, ignoreCase = true)) "pdf" else "mp4"
            val sanitizedId = id.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            val targetFile = File(downloadVaultDir, "${fileType.lowercase()}_${sanitizedId}.$extension")

            var initialEntity = DownloadedItemEntity(
                id = id,
                title = title,
                subtitle = subtitle,
                fileType = fileType,
                remoteUrl = remoteUrl,
                localFilePath = targetFile.absolutePath,
                totalBytes = 0L,
                downloadedBytes = 0L,
                status = DownloadedItemEntity.STATUS_DOWNLOADING,
                createdAt = System.currentTimeMillis()
            )
            downloadedItemDao.insertOrUpdate(initialEntity)

            val isHls = fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true) &&
                    (remoteUrl.contains(".m3u8", ignoreCase = true) || remoteUrl.contains("shikho", ignoreCase = true) || remoteUrl.contains("playlist", ignoreCase = true))

            try {
                if (isHls) {
                    downloadHlsStreamInternal(
                        id = id,
                        initialEntity = initialEntity,
                        targetFile = targetFile,
                        variantUrl = remoteUrl
                    )
                } else {
                    downloadRegularFileInternal(
                        id = id,
                        initialEntity = initialEntity,
                        targetFile = targetFile,
                        remoteUrl = remoteUrl
                    )
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "Download cancelled for id: $id")
                try {
                    if (targetFile.exists()) targetFile.delete()
                    downloadedItemDao.deleteDownloadedItem(id)
                } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for id: $id: ${e.message}", e)
                try {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                } catch (_: Exception) {}

                downloadedItemDao.insertOrUpdate(
                    initialEntity.copy(
                        status = DownloadedItemEntity.STATUS_FAILED,
                        downloadedBytes = 0L
                    )
                )
            } finally {
                activeJobs.remove(id)
            }
        }

        activeJobs[id] = job
        return job
    }

    /**
     * Downloads an HLS stream (.m3u8) by downloading all its TS chunks and saving them into a single playable MP4/TS file.
     */
    private suspend fun downloadHlsStreamInternal(
        id: String,
        initialEntity: DownloadedItemEntity,
        targetFile: File,
        variantUrl: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching HLS playlist from: $variantUrl")

        val request = Request.Builder()
            .url(variantUrl)
            .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
            .addHeader("referer", "https://shikho.com/")
            .addHeader("Referer", "https://shikho.com/")
            .addHeader("Origin", "https://shikho.com")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful || response.body == null) {
            throw Exception("Failed to fetch playlist: HTTP ${response.code}")
        }

        val playlistContent = response.body!!.string()
        var effectivePlaylistContent = playlistContent
        var effectiveBaseUrl = variantUrl

        // If it's a master playlist, parse the child stream
        if (playlistContent.contains("#EXT-X-STREAM-INF")) {
            val lines = playlistContent.lines()
            val subUrls = mutableListOf<String>()
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (!nextLine.startsWith("#") && nextLine.isNotBlank()) {
                        subUrls.add(nextLine)
                    }
                }
            }
            if (subUrls.isNotEmpty()) {
                val chosenSubUrl = subUrls.first()
                val fullSubUrl = resolveUrl(variantUrl, chosenSubUrl)
                effectiveBaseUrl = fullSubUrl

                val subRequest = Request.Builder()
                    .url(fullSubUrl)
                    .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                    .addHeader("referer", "https://shikho.com/")
                    .addHeader("Referer", "https://shikho.com/")
                    .addHeader("Origin", "https://shikho.com")
                    .build()

                val subResponse = httpClient.newCall(subRequest).execute()
                if (subResponse.isSuccessful && subResponse.body != null) {
                    effectivePlaylistContent = subResponse.body!!.string()
                }
            }
        }

        // Parse all TS segments
        val segmentUrls = mutableListOf<String>()
        for (line in effectivePlaylistContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val resolved = resolveUrl(effectiveBaseUrl, trimmed)
                segmentUrls.add(resolved)
            }
        }

        if (segmentUrls.isEmpty()) {
            throw Exception("No video segments found in playlist")
        }

        val totalSegments = segmentUrls.size
        Log.d(TAG, "Found $totalSegments TS segments for id: $id. Starting download...")

        if (targetFile.exists()) {
            targetFile.delete()
        }

        var downloadedBytes = 0L
        val outputStream = FileOutputStream(targetFile, true)
        var lastDbUpdateTime = 0L

        try {
            for (index in 0 until totalSegments) {
                ensureActive() // Check for cancellation

                val segmentUrl = segmentUrls[index]
                val chunkBytes = downloadSegmentWithRetry(segmentUrl)

                outputStream.write(chunkBytes)
                downloadedBytes += chunkBytes.size

                val now = System.currentTimeMillis()
                if (now - lastDbUpdateTime > 400L || index == totalSegments - 1) {
                    lastDbUpdateTime = now
                    val estimatedTotal = ((downloadedBytes.toDouble() / (index + 1)) * totalSegments).toLong()
                    downloadedItemDao.insertOrUpdate(
                        initialEntity.copy(
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (estimatedTotal > 0) estimatedTotal else downloadedBytes,
                            status = DownloadedItemEntity.STATUS_DOWNLOADING
                        )
                    )
                }
            }

            outputStream.flush()

            // Mark completed
            downloadedItemDao.insertOrUpdate(
                initialEntity.copy(
                    downloadedBytes = downloadedBytes,
                    totalBytes = downloadedBytes,
                    status = DownloadedItemEntity.STATUS_COMPLETED,
                    localFilePath = targetFile.absolutePath
                )
            )
            Log.d(TAG, "HLS video download complete! Total size: $downloadedBytes bytes for id: $id")
        } finally {
            try {
                outputStream.close()
            } catch (_: Exception) {}
        }
    }

    private suspend fun downloadSegmentWithRetry(url: String, maxRetries: Int = 3): ByteArray {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                    .addHeader("referer", "https://shikho.com/")
                    .addHeader("Referer", "https://shikho.com/")
                    .addHeader("Origin", "https://shikho.com")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    return response.body!!.bytes()
                } else {
                    throw Exception("HTTP chunk error ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(350L * attempt)
                }
            }
        }
        throw lastException ?: Exception("Failed to download segment chunk after $maxRetries retries")
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }
        return try {
            if (relativeOrAbsolute.startsWith("/")) {
                val uri = URI(baseUrl)
                "${uri.scheme}://${uri.authority}$relativeOrAbsolute"
            } else {
                val baseWithoutFile = baseUrl.substringBeforeLast('/')
                "$baseWithoutFile/$relativeOrAbsolute"
            }
        } catch (_: Exception) {
            val baseWithoutFile = baseUrl.substringBeforeLast('/')
            "$baseWithoutFile/$relativeOrAbsolute"
        }
    }

    /**
     * Downloads a standard regular file (e.g. PDF).
     */
    private suspend fun downloadRegularFileInternal(
        id: String,
        initialEntity: DownloadedItemEntity,
        targetFile: File,
        remoteUrl: String
    ) = withContext(Dispatchers.IO) {
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = Request.Builder()
            .url(remoteUrl)
            .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
            .addHeader("referer", "https://shikho.com/")
            .addHeader("Referer", "https://shikho.com/")
            .addHeader("Origin", "https://shikho.com")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful || response.body == null) {
            throw Exception("HTTP download failed with code: ${response.code}")
        }

        val body = response.body!!
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) contentLength else 0L

        downloadedItemDao.insertOrUpdate(initialEntity.copy(totalBytes = totalBytes))

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var downloadedBytes = 0L
            var lastDbUpdateTime = System.currentTimeMillis()

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                ensureActive()
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastDbUpdateTime > 350L || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                    lastDbUpdateTime = now
                    val currentTotal = if (totalBytes > 0) totalBytes else downloadedBytes
                    downloadedItemDao.insertOrUpdate(
                        initialEntity.copy(
                            downloadedBytes = downloadedBytes,
                            totalBytes = currentTotal,
                            status = DownloadedItemEntity.STATUS_DOWNLOADING
                        )
                    )
                }
            }

            outputStream.flush()

            val finalTotal = if (totalBytes > 0) totalBytes else downloadedBytes
            downloadedItemDao.insertOrUpdate(
                initialEntity.copy(
                    downloadedBytes = downloadedBytes,
                    totalBytes = finalTotal,
                    status = DownloadedItemEntity.STATUS_COMPLETED,
                    localFilePath = targetFile.absolutePath
                )
            )
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Cancel an active download.
     */
    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        coroutineScope.launch {
            try {
                val item = downloadedItemDao.getDownloadedItemByIdOnce(id)
                if (item != null && item.status == DownloadedItemEntity.STATUS_DOWNLOADING) {
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    downloadedItemDao.deleteDownloadedItem(id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling download $id: ${e.message}")
            }
        }
    }

    /**
     * Delete a downloaded file completely from vault and database.
     */
    fun deleteDownloadedFile(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        coroutineScope.launch {
            try {
                val item = downloadedItemDao.getDownloadedItemByIdOnce(id)
                if (item != null) {
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                downloadedItemDao.deleteDownloadedItem(id)
                Log.d(TAG, "Deleted downloaded item: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting downloaded file: $id: ${e.message}")
            }
        }
    }

    /**
     * Reactive flow check if item is completed downloaded.
     */
    fun isItemDownloaded(id: String): Flow<Boolean> {
        return downloadedItemDao.isItemDownloaded(id)
    }

    /**
     * Single check if item is completed downloaded and file exists.
     */
    suspend fun isItemDownloadedOnce(id: String): Boolean {
        val downloaded = downloadedItemDao.isItemDownloadedOnce(id)
        if (!downloaded) return false
        val item = downloadedItemDao.getDownloadedItemByIdOnce(id) ?: return false
        val file = File(item.localFilePath)
        return file.exists() && file.length() > 0
    }

    fun getDownloadedItemById(id: String): Flow<DownloadedItemEntity?> {
        return downloadedItemDao.getDownloadedItemById(id)
    }

    suspend fun getDownloadedItemByIdOnce(id: String): DownloadedItemEntity? {
        val item = downloadedItemDao.getDownloadedItemByIdOnce(id) ?: return null
        val file = File(item.localFilePath)
        if (item.status == DownloadedItemEntity.STATUS_COMPLETED && (!file.exists() || file.length() == 0L)) {
            // File was removed from storage somehow, cleanup
            downloadedItemDao.deleteDownloadedItem(id)
            return null
        }
        return item
    }

    fun getAllCompletedDownloads(): Flow<List<DownloadedItemEntity>> {
        return downloadedItemDao.getAllCompletedDownloads()
    }

    fun getAllDownloads(): Flow<List<DownloadedItemEntity>> {
        return downloadedItemDao.getAllDownloads()
    }

    /**
     * Gets existing local file if downloaded and valid.
     */
    fun getLocalFile(item: DownloadedItemEntity): File? {
        val file = File(item.localFilePath)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Helper to format bytes into readable MB/KB string with Bengali digits.
     */
    fun formatFileSize(bytes: Long, inBengali: Boolean = true): String {
        if (bytes <= 0) return if (inBengali) "০ মেগাবাইট" else "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        val formatted = when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            else -> String.format(java.util.Locale.US, "%.0f KB", kb)
        }

        return if (inBengali) {
            val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
            var res = formatted
            for (i in 0..9) {
                res = res.replace('0' + i, bnDigits[i])
            }
            res.replace("GB", "জিবি").replace("MB", "মেগাবাইট").replace("KB", "কেবি")
        } else {
            formatted
        }
    }
}
