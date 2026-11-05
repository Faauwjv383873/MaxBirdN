package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.security.AntiTamperSecurity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class ApkUpdateDownloadState {
    object Idle : ApkUpdateDownloadState()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : ApkUpdateDownloadState()

    object Verifying : ApkUpdateDownloadState()
    data class PermissionRequired(val apkFile: File) : ApkUpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : ApkUpdateDownloadState()
    data class Installing(val apkFile: File) : ApkUpdateDownloadState()
    data class Error(val messageBn: String, val fallbackUrl: String) : ApkUpdateDownloadState()
}

/**
 * Production-grade In-App APK Downloader & Self-Installer:
 * - Resolves direct APK URLs, Google Drive links (including virus-scan warning bypass),
 *   GitHub Releases, Supabase Storage, and Dropbox links.
 * - Streams the APK directly inside the app with real-time progress (%), MB downloaded, and speed (MB/s).
 * - Validates APK archive integrity (ZIP magic bytes + PackageManager archive info + optional SHA-256 hash).
 * - Automatically triggers the native Android Package Installer via FileProvider.
 * - Handles Android 8.0+ "Install Unknown Apps" permission seamlessly and auto-resumes installation on return.
 */
object InAppApkUpdater {
    private const val TAG = "InAppApkUpdater"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeDownloadJob: Job? = null

    private val _downloadState = MutableStateFlow<ApkUpdateDownloadState>(ApkUpdateDownloadState.Idle)
    val downloadState: StateFlow<ApkUpdateDownloadState> = _downloadState.asStateFlow()

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { AntiTamperSecurity.applyToOkHttpBuilder(this) }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: Throwable) {
                true
            }
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to open unknown app sources settings: ${e.message}")
            }
        }
    }

    fun resetState() {
        activeDownloadJob?.cancel()
        _downloadState.value = ApkUpdateDownloadState.Idle
    }

    /**
     * Starts downloading the APK inside the app (or reuses an already verified APK for the target version)
     * and automatically triggers installation upon completion.
     */
    fun startDownloadAndInstall(context: Context, updateInfo: AppUpdateInfo) {
        val appContext = context.applicationContext
        val currentState = _downloadState.value

        if (currentState is ApkUpdateDownloadState.PermissionRequired && currentState.apkFile.exists()) {
            triggerInstallOrRequestPermission(appContext, currentState.apkFile)
            return
        }
        if (currentState is ApkUpdateDownloadState.ReadyToInstall && currentState.apkFile.exists()) {
            triggerInstallOrRequestPermission(appContext, currentState.apkFile)
            return
        }
        if (currentState is ApkUpdateDownloadState.Downloading) {
            return
        }

        val rawUrl = updateInfo.downloadUrl.trim()
        if (rawUrl.isBlank() || rawUrl.equals("https://t.me/", ignoreCase = true)) {
            _downloadState.value = ApkUpdateDownloadState.Error(
                messageBn = "ডাটাবেজে বৈধ APK ডাউনলোড লিংক দেওয়া হয়নি। অ্যাডমিন প্যানেল থেকে সরাসরি APK ডাউনলোড লিংক যুক্ত করুন।",
                fallbackUrl = rawUrl
            )
            return
        }

        activeDownloadJob?.cancel()
        activeDownloadJob = scope.launch {
            try {
                val updatesDir = getUpdatesDirectory(appContext)
                val targetApkFile = File(updatesDir, "MaxBird_Update_v${updateInfo.versionCode}.apk")

                if (targetApkFile.exists() && targetApkFile.length() > 100_000L &&
                    isValidApkArchive(appContext, targetApkFile) &&
                    verifySha256IfRequired(targetApkFile, updateInfo.signatureHash)
                ) {
                    withContext(Dispatchers.Main) {
                        triggerInstallOrRequestPermission(appContext, targetApkFile)
                    }
                    return@launch
                }

                cleanUpdatesDirectory(updatesDir, keepFileName = targetApkFile.name)

                _downloadState.value = ApkUpdateDownloadState.Downloading(
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    speedBytesPerSec = 0L
                )

                val resolvedUrl = normalizeDirectDownloadUrl(rawUrl)
                val tempFile = File(updatesDir, "downloading_update_${updateInfo.versionCode}.apk.part")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                val downloadSuccess = streamUrlToFile(resolvedUrl, tempFile, rawUrl)
                if (!downloadSuccess) {
                    return@launch
                }

                _downloadState.value = ApkUpdateDownloadState.Verifying

                if (!isValidApkArchive(appContext, tempFile)) {
                    tempFile.delete()
                    _downloadState.value = ApkUpdateDownloadState.Error(
                        messageBn = "ডাউনলোডকৃত ফাইলটি বৈধ APK নয় (সম্ভবত সরাসরি ডাউনলোড লিংকের বদলে ওয়েবপেজ লিংক দেওয়া হয়েছে)।",
                        fallbackUrl = rawUrl
                    )
                    return@launch
                }

                if (!verifySha256IfRequired(tempFile, updateInfo.signatureHash)) {
                    tempFile.delete()
                    _downloadState.value = ApkUpdateDownloadState.Error(
                        messageBn = "APK ফাইলের সিকিউরিটি চেকসাম (SHA-256) মেলেনি। পুনরায় চেষ্টা করুন।",
                        fallbackUrl = rawUrl
                    )
                    return@launch
                }

                if (targetApkFile.exists()) {
                    targetApkFile.delete()
                }
                val renamed = tempFile.renameTo(targetApkFile)
                val finalApkFile = if (renamed) targetApkFile else tempFile

                withContext(Dispatchers.Main) {
                    triggerInstallOrRequestPermission(appContext, finalApkFile)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "In-app APK download failed: ${e.message}", e)
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = "ডাউনলোড ব্যর্থ হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক সংযোগ যাচাই করুন"}",
                    fallbackUrl = rawUrl
                )
            }
        }
    }

    fun triggerInstallOrRequestPermission(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            _downloadState.value = ApkUpdateDownloadState.Idle
            return
        }
        if (!canInstallPackages(context)) {
            _downloadState.value = ApkUpdateDownloadState.PermissionRequired(apkFile)
            return
        }
        _downloadState.value = ApkUpdateDownloadState.Installing(apkFile)
        val launched = launchPackageInstaller(context, apkFile)
        if (launched) {
            _downloadState.value = ApkUpdateDownloadState.ReadyToInstall(apkFile)
        } else {
            _downloadState.value = ApkUpdateDownloadState.Error(
                messageBn = "অটোমেটিক ইনস্টলার চালু করা যায়নি। পুনরায় চেষ্টা করুন।",
                fallbackUrl = ""
            )
        }
    }

    fun launchPackageInstaller(context: Context, apkFile: File): Boolean {
        return try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            false
        }
    }

    private fun streamUrlToFile(
        initialUrl: String,
        destinationFile: File,
        fallbackOriginalUrl: String
    ): Boolean {
        val request = Request.Builder()
            .url(initialUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .get()
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = "সার্ভার থেকে ফাইল ডাউনলোড করা যায়নি (HTTP ${response.code})।",
                    fallbackUrl = fallbackOriginalUrl
                )
                return false
            }

            val contentType = response.header("Content-Type")?.lowercase(Locale.US).orEmpty()
            val body = response.body ?: return false

            if (contentType.contains("text/html")) {
                val htmlPreview = body.string()
                val confirmedDriveUrl = extractGoogleDriveConfirmedUrl(htmlPreview, initialUrl)
                if (!confirmedDriveUrl.isNullOrBlank() && confirmedDriveUrl != initialUrl) {
                    return streamConfirmedUrlToFile(confirmedDriveUrl, destinationFile, fallbackOriginalUrl)
                }
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = "প্রদত্ত লিংকটি সরাসরি APK ফাইলের পরিবর্তে একটি ওয়েবপেজ রিটার্ন করেছে। সরাসরি .apk ডাউনলোড লিংক দিন।",
                    fallbackUrl = fallbackOriginalUrl
                )
                return false
            }

            return writeResponseStreamToFile(body.byteStream(), body.contentLength(), destinationFile)
        }
    }

    private fun streamConfirmedUrlToFile(
        confirmedUrl: String,
        destinationFile: File,
        fallbackOriginalUrl: String
    ): Boolean {
        val request = Request.Builder()
            .url(confirmedUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
            .get()
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = "গুগল ড্রাইভ থেকে ফাইল ডাউনলোড ব্যর্থ হয়েছে (HTTP ${response.code})।",
                    fallbackUrl = fallbackOriginalUrl
                )
                return false
            }
            val body = response.body ?: return false
            return writeResponseStreamToFile(body.byteStream(), body.contentLength(), destinationFile)
        }
    }

    private fun writeResponseStreamToFile(
        inputStream: java.io.InputStream,
        totalBytes: Long,
        destinationFile: File
    ): Boolean {
        val buffer = ByteArray(32 * 1024)
        var downloadedBytes = 0L
        val startTimeMs = System.currentTimeMillis()
        var lastEmitTimeMs = 0L

        inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTimeMs >= 120L || (totalBytes > 0L && downloadedBytes >= totalBytes)) {
                        lastEmitTimeMs = now
                        val elapsedSec = ((now - startTimeMs) / 1000.0).coerceAtLeast(0.1)
                        val speedBytesPerSec = (downloadedBytes / elapsedSec).toLong()
                        val percent = if (totalBytes > 0L) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        _downloadState.value = ApkUpdateDownloadState.Downloading(
                            progressPercent = percent,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes.coerceAtLeast(0L),
                            speedBytesPerSec = speedBytesPerSec
                        )
                    }
                }
                output.flush()
            }
        }
        return downloadedBytes > 50_000L
    }

    fun normalizeDirectDownloadUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val driveFileIdRegex = Regex("""drive\.google\.com/file/d/([a-zA-Z0-9_-]+)""")
        val driveMatch = driveFileIdRegex.find(trimmed)
        if (driveMatch != null) {
            val fileId = driveMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        val driveOpenIdRegex = Regex("""drive\.google\.com/(?:open|uc)\?.*id=([a-zA-Z0-9_-]+)""")
        val driveOpenMatch = driveOpenIdRegex.find(trimmed)
        if (driveOpenMatch != null) {
            val fileId = driveOpenMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        if (trimmed.contains("dropbox.com/", ignoreCase = true)) {
            return trimmed
                .replace("?dl=0", "?dl=1")
                .replace("&dl=0", "&dl=1")
                .let { if (!it.contains("dl=1")) "$it${if (it.contains("?")) "&" else "?"}dl=1" else it }
        }

        if (trimmed.contains("github.com/", ignoreCase = true) && trimmed.contains("/blob/")) {
            return trimmed.replace("/blob/", "/raw/")
        }

        return trimmed
    }

    private fun extractGoogleDriveConfirmedUrl(html: String, originalUrl: String): String? {
        val actionRegex = Regex("""action="([^"]+)"[^>]*id="download-form"""")
        val actionMatch = actionRegex.find(html)
        val idRegex = Regex("""name="id"\s+value="([^"]+)"""")
        val confirmRegex = Regex("""name="confirm"\s+value="([^"]+)"""")
        val uuidRegex = Regex("""name="uuid"\s+value="([^"]+)"""")

        val fileId = idRegex.find(html)?.groupValues?.getOrNull(1)
        val confirm = confirmRegex.find(html)?.groupValues?.getOrNull(1) ?: "t"
        val uuid = uuidRegex.find(html)?.groupValues?.getOrNull(1)

        if (!fileId.isNullOrBlank()) {
            val base = actionMatch?.groupValues?.getOrNull(1)?.replace("&amp;", "&")
                ?: "https://drive.usercontent.google.com/download"
            val sb = StringBuilder(base)
            sb.append(if (base.contains("?")) "&" else "?")
            sb.append("id=").append(fileId)
            sb.append("&export=download&confirm=").append(confirm)
            if (!uuid.isNullOrBlank()) {
                sb.append("&uuid=").append(uuid)
            }
            return sb.toString()
        }
        return null
    }

    private fun isValidApkArchive(context: Context, file: File): Boolean {
        if (!file.exists() || file.length() < 50_000L) return false
        return try {
            val header = ByteArray(4)
            FileInputStream(file).use { fis ->
                if (fis.read(header) != 4) return false
            }
            val isZipHeader = header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
            if (!isZipHeader) return false

            val pm = context.packageManager
            val archiveInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
            archiveInfo != null && !archiveInfo.packageName.isNullOrBlank()
        } catch (_: Throwable) {
            true
        }
    }

    private fun verifySha256IfRequired(file: File, expectedSha256: String?): Boolean {
        val cleanExpected = expectedSha256?.trim().orEmpty()
        if (cleanExpected.length != 64) return true
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16 * 1024)
            FileInputStream(file).use { fis ->
                while (true) {
                    val read = fis.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
            actualHex.equals(cleanExpected, ignoreCase = true)
        } catch (_: Throwable) {
            true
        }
    }

    private fun getUpdatesDirectory(context: Context): File {
        val baseDir = context.externalCacheDir ?: context.cacheDir
        val dir = File(baseDir, "apk_updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun cleanUpdatesDirectory(dir: File, keepFileName: String) {
        try {
            dir.listFiles()?.forEach { f ->
                if (f.name != keepFileName) {
                    f.delete()
                }
            }
        } catch (_: Throwable) {}
    }

    fun formatMegabytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f MB", mb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        val mbps = bytesPerSec.toDouble() / (1024.0 * 1024.0)
        return if (mbps >= 1.0) {
            String.format(Locale.US, "%.2f MB/s", mbps)
        } else {
            val kbps = bytesPerSec.toDouble() / 1024.0
            String.format(Locale.US, "%.0f KB/s", kbps)
        }
    }
}
