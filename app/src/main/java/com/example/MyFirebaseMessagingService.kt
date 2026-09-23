package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.auth.SessionManager
import com.example.database.NotificationHistoryEntity
import com.example.database.NotificationHistoryRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        const val CHANNEL_ID = "shikho_push_notifications"
        const val CHANNEL_NAME = "Shikho Notifications"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔥 FCM Token received: $token")
        
        val firebaseApp = com.google.firebase.FirebaseApp.getInstance()
        Log.d(TAG, "🔥 Firebase Project ID: ${firebaseApp.options.projectId}")
        Log.d(TAG, "🔥 GCM Sender ID: ${firebaseApp.options.gcmSenderId}")
        Log.d(TAG, "🔥 App ID: ${firebaseApp.options.applicationId}")
        
        if (firebaseApp.options.projectId != "shikho-tech") {
            Log.e(TAG, "❌ WRONG FIREBASE PROJECT! Expected shikho-tech but got ${firebaseApp.options.projectId}")
            Log.e(TAG, "❌ google-services.json is wrong — check the package_name and project_id")
        } else {
            Log.d(TAG, "✅ Firebase correctly initialized with shikho-tech")
        }
        
        val sessionManager = SessionManager(applicationContext)
        sessionManager.setFcmToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "📩 From: ${remoteMessage.from}")
        Log.d(TAG, "📩 Data: ${remoteMessage.data}")
        
        val data = remoteMessage.data

        // Extract title and body
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: "শিখুন Shikho"
            
        val messageBody = remoteMessage.notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: ""

        val imageUrl = remoteMessage.notification?.imageUrl?.toString()
            ?: data["image"]
            ?: data["imageUrl"]

        val type = data["type"] ?: data["event"] ?: "live_class"
        val deepLink = data["deep_link"] ?: data["link"] ?: data["url"]

        val dataJson = try {
            if (data.isNotEmpty()) {
                JSONObject(data as Map<*, *>).toString()
            } else null
        } catch (_: Exception) {
            null
        }

        // Save to Notification History Room Database BEFORE showing notification
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

        if (messageBody.isNotBlank()) {
            sendNotification(title, messageBody, data)
        }
    }

    private fun sendNotification(title: String, messageBody: String, dataPayload: Map<String, String>) {
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
            0,
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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Notification Channel required for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shikho Live Class and Exam Notifications"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
