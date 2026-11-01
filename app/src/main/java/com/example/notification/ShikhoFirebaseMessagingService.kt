package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.auth.SessionManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Service to handle incoming Firebase Cloud Messaging (FCM) notifications and token refreshes.
 */
class ShikhoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ShikhoFCMService"
        const val EXTRA_NOTIFICATION_URL = "extra_notification_url"
        const val EXTRA_NOTIFICATION_DEEPLINK = "extra_notification_deeplink"
        const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        const val EXTRA_CONTENT_ID = "extra_content_id"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM Token: $token")
        val sessionManager = SessionManager(applicationContext)
        sessionManager.setFcmToken(token)

        // Sync all subscriptions with the new token (FCM topics + OneSignal Player + Supabase)
        ShikhoNotificationManager.syncAllTopicSubscriptions(applicationContext)
        OneSignalPushManager.triggerSyncAsync(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")
        ShikhoNotificationManager.logTerminal("PUSH", "📩 Push Notification Received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        // Parse OneSignal's custom JSON payload (`{"i": "notif_id", "a": {...}}`) if present
        val oneSignalAdditionalData = try {
            val customRaw = data["custom"]
            if (!customRaw.isNullOrBlank() && customRaw.trim().startsWith("{")) {
                org.json.JSONObject(customRaw).optJSONObject("a")
            } else null
        } catch (_: Throwable) {
            null
        }

        val eventType = (
            data["event_type"]
                ?: data["type"]
                ?: data["content_type"]
                ?: oneSignalAdditionalData?.optString("event_type", "")
                ?: oneSignalAdditionalData?.optString("type", "")
                ?: ""
        ).trim()

        // Critical Admin -> Student notifications (Device Approved/Rejected, Course Approved, Payment Verified)
        // must ALWAYS be shown even if general marketing notifications are toggled off.
        val isCriticalAdminUpdate = eventType.contains("DEVICE", ignoreCase = true) ||
            eventType.contains("COURSE", ignoreCase = true) ||
            eventType.contains("PAYMENT", ignoreCase = true) ||
            eventType.contains("TRIAL", ignoreCase = true) ||
            eventType.contains("INSTALLMENT", ignoreCase = true) ||
            eventType.contains("APPROV", ignoreCase = true) ||
            eventType.contains("REJECT", ignoreCase = true) ||
            data.containsKey("alert") ||
            data.containsKey("custom")

        val sessionManager = SessionManager(applicationContext)
        if (!isCriticalAdminUpdate && !sessionManager.isAllNotificationsEnabled()) {
            Log.d(TAG, "Push notifications are disabled in user settings. Skipping display.")
            return
        }

        val title = notification?.title
            ?: data["title"]
            ?: data["heading"]
            ?: oneSignalAdditionalData?.optString("title", "")?.takeIf { it.isNotBlank() }
            ?: "শিখো নোটিফিকেশন"

        // Note: OneSignal sends the notification body in `data["alert"]`!
        val body = notification?.body
            ?: data["alert"]
            ?: data["body"]
            ?: data["message"]
            ?: data["description"]
            ?: oneSignalAdditionalData?.optString("body", "")?.takeIf { it.isNotBlank() }
            ?: oneSignalAdditionalData?.optString("message", "")?.takeIf { it.isNotBlank() }
            ?: ""

        val imageUrl = notification?.imageUrl?.toString()
            ?: data["bicon"]
            ?: data["big_picture"]
            ?: data["image"]
            ?: data["image_url"]
            ?: data["banner"]
            ?: oneSignalAdditionalData?.optString("image_url", "")?.takeIf { it.isNotBlank() }

        val deeplink = data["deeplink"]
            ?: data["url"]
            ?: data["link"]
            ?: data["click_action"]
            ?: oneSignalAdditionalData?.optString("deeplink", "")?.takeIf { it.isNotBlank() }
            ?: ""

        val contentId = data["content_id"]
            ?: data["program_id"]
            ?: data["id"]
            ?: data["lesson_id"]
            ?: oneSignalAdditionalData?.optString("program_id", "")
            ?: ""

        // Process and display notification + refresh device/course status in background scope
        CoroutineScope(Dispatchers.IO).launch {
            // Automatically sync device approval and course commerce state when Admin sends a status update push!
            runCatching {
                com.example.security.DeviceActivationRepository.checkStudentVerificationStatus(applicationContext)
            }
            runCatching {
                com.example.course.CourseCommerceRepository.fetchCourseCommerceStatus(applicationContext)
            }

            val bitmap = if (!imageUrl.isNullOrBlank()) {
                downloadBitmap(imageUrl)
            } else null

            displayNotification(
                title = title,
                body = body,
                imageUrl = imageUrl,
                largeBitmap = bitmap,
                deeplink = deeplink,
                type = eventType,
                contentId = contentId
            )
        }
    }

    private fun displayNotification(
        title: String,
        body: String,
        imageUrl: String?,
        largeBitmap: Bitmap?,
        deeplink: String,
        type: String,
        contentId: String
    ) {
        val channelId = if (type.contains("live", ignoreCase = true) || deeplink.contains("live", ignoreCase = true)) {
            ShikhoNotificationManager.CHANNEL_LIVE_CLASSES
        } else {
            ShikhoNotificationManager.CHANNEL_PUSH_NOTIFICATIONS
        }

        // Create PendingIntent pointing to MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_URL, deeplink)
            putExtra(EXTRA_NOTIFICATION_DEEPLINK, deeplink)
            putExtra(EXTRA_NOTIFICATION_TYPE, type)
            putExtra(EXTRA_CONTENT_ID, contentId)
            if (deeplink.isNotBlank()) {
                data = Uri.parse(deeplink)
            }
        }

        val requestCode = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val appLogoBitmap = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_shikho_notification_small)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSound)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pendingIntent)

        if (appLogoBitmap != null) {
            builder.setLargeIcon(appLogoBitmap)
        }

        // If a banner image was downloaded, use BigPictureStyle; otherwise, use BigTextStyle
        if (largeBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(largeBitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
            )
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = Random.nextInt(1000, 999999)
        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "Displayed notification (id: $notificationId): $title")
    }

    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to download notification image: ${e.message}")
            null
        }
    }
}
