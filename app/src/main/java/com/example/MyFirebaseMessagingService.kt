package com.example

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.auth.SessionManager
import com.example.database.NotificationHistoryEntity
import com.example.database.NotificationHistoryRepository
import com.example.notification.FcmTopicManager
import com.example.notification.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        const val CHANNEL_ID = NotificationHelper.CHANNEL_ID
        const val CHANNEL_NAME = NotificationHelper.CHANNEL_NAME
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔑 Refreshed FCM Token: $token")
        
        val sessionManager = SessionManager(applicationContext)
        sessionManager.setFcmToken(token)
        
        // Re-synchronize and subscribe to all topics on token refresh
        FcmTopicManager.syncAllTopics(sessionManager)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "📩 From: ${remoteMessage.from}")
        Log.d(TAG, "📩 Data: ${remoteMessage.data}")
        Log.d(TAG, "📩 Notification: title='${remoteMessage.notification?.title}', body='${remoteMessage.notification?.body}'")
        
        val data = remoteMessage.data

        // Extract title and body from either remoteMessage.notification or remoteMessage.data
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: data["subject"]
            ?: "শিখুন Shikho"
            
        val messageBody = remoteMessage.notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: data["description"]
            ?: ""

        // Extract banner image URL
        val imageUrl = remoteMessage.notification?.imageUrl?.toString()
            ?: data["image"]
            ?: data["imageUrl"]
            ?: data["picture"]
            ?: data["banner"]
            ?: data["big_picture"]
            ?: data["media_url"]

        val type = data["type"] ?: data["event"] ?: "live_class"
        val deepLink = data["deep_link"] ?: data["link"] ?: data["url"]

        val dataJson = try {
            if (data.isNotEmpty()) {
                JSONObject(data as Map<*, *>).toString()
            } else null
        } catch (_: Exception) {
            null
        }

        // 1. Save to Room Database
        try {
            val repository = NotificationHistoryRepository.getInstance(applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entity = NotificationHistoryEntity(
                        title = title,
                        body = messageBody,
                        imageUrl = imageUrl,
                        type = type,
                        dataJson = dataJson,
                        deepLink = deepLink,
                        receivedAt = System.currentTimeMillis(),
                        isRead = false,
                        isClicked = false
                    )
                    val insertedId = repository.save(entity)
                    Log.d(TAG, "💾 Saved notification to history DB with ID: $insertedId")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to save notification to history DB: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize repository for notification saving: ${e.message}", e)
        }

        // 2. Display the system notification with BigPictureStyle if an image exists
        if (title.isNotBlank() || messageBody.isNotBlank()) {
            sendNotification(title, messageBody, imageUrl, data)
        }
    }

    private fun sendNotification(
        title: String,
        messageBody: String,
        imageUrl: String?,
        dataPayload: Map<String, String>
    ) {
        // Ensure channel exists before dispatching
        NotificationHelper.createNotificationChannel(applicationContext)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            for ((key, value) in dataPayload) {
                putExtra(key, value)
            }
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % 100000).toInt(),
            intent,
            pendingIntentFlags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        // Download and attach big image banner if present
        val bitmap = getBitmapFromUrl(imageUrl)
        if (bitmap != null) {
            notificationBuilder.setLargeIcon(bitmap)
            notificationBuilder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(messageBody)
            )
        } else if (messageBody.isNotBlank()) {
            notificationBuilder.setStyle(
                NotificationCompat.BigTextStyle().bigText(messageBody)
            )
        }

        try {
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notificationBuilder.build())
            Log.d(TAG, "🔔 System notification shown: '$title'")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException showing notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying notification: ${e.message}", e)
        }
    }

    private fun getBitmapFromUrl(imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading notification image ($imageUrl): ${e.message}")
            null
        }
    }
}
