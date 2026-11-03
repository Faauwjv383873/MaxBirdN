package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.security.DeviceActivationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val minSupportedVersionCode: Int,
    val isForce: Boolean,
    val titleBn: String,
    val changelogBn: String,
    val downloadUrl: String,
    val buttonText: String,
    val signatureHash: String?
)

object AppUpdateRepository {
    private const val TAG = "AppUpdateRepository"
    private val httpClient = OkHttpClient.Builder()
        .apply { com.example.security.AntiTamperSecurity.applyToOkHttpBuilder(this) }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Checks whether the installed app version differs from the required version in the database.
     * Enforces exact version matching: if the database specifies version 7 and the user has
     * version 8 (or version 6), this returns true so the user must install the exact database version.
     */
    fun isUpdateRequired(
        info: AppUpdateInfo?,
        installedVersionCode: Int = BuildConfig.VERSION_CODE,
        installedVersionName: String = BuildConfig.VERSION_NAME
    ): Boolean {
        if (info == null) return false
        val dbName = info.versionName.trim().removePrefix("v").removePrefix("V")
        val appName = installedVersionName.trim().removePrefix("v").removePrefix("V")

        val codeMatches = info.versionCode <= 0 ||
            info.versionCode == installedVersionCode ||
            (dbName.equals(appName, ignoreCase = true) &&
                (installedVersionCode % 100 == info.versionCode || info.versionCode % 100 == installedVersionCode))

        val nameMatches = dbName.isBlank() ||
            dbName.equals(appName, ignoreCase = true) ||
            (info.versionCode == installedVersionCode && dbName == info.versionCode.toString())

        return !(codeMatches && nameMatches)
    }

    fun isDowngradeRequired(
        info: AppUpdateInfo,
        installedVersionCode: Int = BuildConfig.VERSION_CODE
    ): Boolean {
        return info.versionCode in 1 until installedVersionCode
    }

    suspend fun fetchLatestUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                Log.w(TAG, "Supabase config missing for update check.")
                return@withContext null
            }

            // Order by created_at.desc so if admin sets a lower required version (e.g. 7 after 8),
            // the newest active configuration is always fetched.
            val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/app_updates?select=*&is_active=eq.true&order=created_at.desc&limit=1"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch updates: ${response.code} - ${response.message}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val jsonArray = JSONArray(bodyStr)
                if (jsonArray.length() == 0) return@withContext null

                val obj = jsonArray.getJSONObject(0)
                val versionCode = obj.optInt("latest_version_code", 0)
                val versionName = obj.optString("latest_version_name", "1.0.0")
                val minSupportedCode = obj.optInt("min_supported_version_code", versionCode)
                // Strict version enforcement: only the exact active version in the database is allowed
                val isForce = true
                val titleBn = obj.optString("title", "নির্ধারিত সংস্করণ ইনস্টল করুন")
                val changelogBn = obj.optString("changelog", "অ্যাপে নতুন ফিচার এবং সিকিউরিটি আপডেট যুক্ত করা হয়েছে।")
                val downloadUrl = obj.optString("download_url", "https://t.me/")
                val buttonText = obj.optString("button_text", "এখনই ডাউনলোড ও ইনস্টল করুন")
                val signatureHash = obj.optString("apk_checksum_sha256", null)

                return@withContext AppUpdateInfo(
                    versionCode = versionCode,
                    versionName = versionName,
                    minSupportedVersionCode = minSupportedCode,
                    isForce = isForce,
                    titleBn = titleBn,
                    changelogBn = changelogBn,
                    downloadUrl = downloadUrl,
                    buttonText = buttonText,
                    signatureHash = signatureHash
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception fetching update: ${e.message}", e)
            null
        }
    }

    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to open update url: ${e.message}")
        }
    }
}
