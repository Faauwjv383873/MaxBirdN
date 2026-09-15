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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
        private const val BUFFER_SIZE = 8192 // 8KB buffer

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
    }

    /**
     * Start downloading a file (Video or PDF) into the secured internal vault directory.
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

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                // Delete if old corrupted file exists
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

                initialEntity = initialEntity.copy(totalBytes = totalBytes)
                downloadedItemDao.insertOrUpdate(initialEntity)

                inputStream = body.byteStream()
                outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastDbUpdateTime = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    ensureActive() // Throw CancellationException if cancelled
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    // Throttle DB updates to once every 300ms or when chunk exceeds threshold
                    if (now - lastDbUpdateTime > 300L || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
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

                // Mark completed
                val finalTotal = if (totalBytes > 0) totalBytes else downloadedBytes
                downloadedItemDao.insertOrUpdate(
                    initialEntity.copy(
                        downloadedBytes = downloadedBytes,
                        totalBytes = finalTotal,
                        status = DownloadedItemEntity.STATUS_COMPLETED,
                        localFilePath = targetFile.absolutePath
                    )
                )
                Log.d(TAG, "Download completed successfully: $id -> ${targetFile.absolutePath}")

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
                try {
                    inputStream?.close()
                } catch (_: Exception) {}
                try {
                    outputStream?.close()
                } catch (_: Exception) {}
                activeJobs.remove(id)
            }
        }

        activeJobs[id] = job
        return job
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
     * Helper to format bytes into readable MB/KB string with optional Bengali digits.
     */
    fun formatFileSize(bytes: Long, inBengali: Boolean = true): String {
        if (bytes <= 0) return if (inBengali) "০ কিলোবাইট" else "0 KB"
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
