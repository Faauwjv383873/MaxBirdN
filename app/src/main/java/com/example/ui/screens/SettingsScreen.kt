package com.example.ui.screens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.MainActivity
import com.example.auth.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangeSyllabus: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCourseEnrollment: () -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userName = remember { sessionManager.getUserFullName() ?: "শিক্ষার্থী" }
    val userClass = remember { sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "একাদশ শ্রেণি" }
    val userGroup = remember { sessionManager.getUserGroup() ?: "মানবিক" }
    val userBatch = remember { sessionManager.getUserBatchId() ?: "" }
    val userAvatar = remember { sessionManager.getUserAvatar() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sendTestPushNotification(context)
        } else {
            Toast.makeText(context, "নোটিফিকেশন পারমিশন প্রয়োজন", Toast.LENGTH_SHORT).show()
        }
    }

    val onTestNotificationClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                sendTestPushNotification(context)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            sendTestPushNotification(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "সেটিংস",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Profile Card Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNavigateToEditProfile() },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!userAvatar.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(userAvatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Text(
                                        text = userName.firstOrNull()?.toString()?.uppercase() ?: "U",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        } else {
                            Text(
                                text = userName.firstOrNull()?.toString()?.uppercase() ?: "U",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = listOf(userClass, userGroup).filter { it.isNotBlank() }.joinToString(" • "),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Academic Settings Group
            SettingsSection(title = "একাডেমিক সেটিংস") {
                // 1. প্রোফাইল এডিট (Edit Profile - above Change Syllabus)
                SettingsRowItem(
                    icon = Icons.Default.Edit,
                    iconTint = Color(0xFF7C3AED),
                    iconBg = Color(0xFFEDE9FE),
                    title = "প্রোফাইল এডিট",
                    subtitle = "ব্যক্তিগত ও শিক্ষাপ্রতিষ্ঠানের তথ্য পরিবর্তন করো",
                    badge = "এডিট",
                    onClick = onNavigateToEditProfile
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 2. সিলেবাস পরিবর্তন (Change Syllabus)
                SettingsRowItem(
                    icon = Icons.Default.SwapCalls,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    title = "সিলেবাস পরিবর্তন",
                    subtitle = listOf(userClass, userGroup, userBatch).filter { it.isNotBlank() }.joinToString(" • "),
                    badge = "পরিবর্তন",
                    onClick = onNavigateToChangeSyllabus
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 3. প্রোফাইল বিবরণ
                SettingsRowItem(
                    icon = Icons.Default.Person,
                    iconTint = Color(0xFF0284C7),
                    iconBg = Color(0xFFE0F2FE),
                    title = "প্রোফাইল বিবরণ",
                    subtitle = "নাম, প্রতিষ্ঠান ও অ্যাকাউন্টের তথ্য",
                    onClick = onNavigateToProfile
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 4. কোর্সে ভর্তি (Course Enrollment / Admission Details)
                SettingsRowItem(
                    icon = Icons.Default.CardMembership,
                    iconTint = Color(0xFF16A34A),
                    iconBg = Color(0xFFDCFCE7),
                    title = "কোর্সে ভর্তি",
                    subtitle = "ভর্তি হওয়া কোর্স, কোয়ার্টার ও মেয়াদের বিবরণ",
                    badge = "বিস্তারিত",
                    onClick = onNavigateToCourseEnrollment
                )
            }

            // Account & Preferences Section
            SettingsSection(title = "অ্যাকাউন্ট ও নিরাপত্তা") {
                SettingsRowItem(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = Color(0xFFD97706),
                    iconBg = Color(0xFFFEF3C7),
                    title = "টেস্ট পুশ নোটিফিকেশন",
                    subtitle = "লাইভ কোর্স নোটিফিকেশন সাবস্ক্রিপশন পরোক্ষভাবে টেস্ট করুন",
                    badge = "টেস্ট করুন",
                    onClick = onTestNotificationClick
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Logout
                SettingsRowItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    title = "লগআউট",
                    subtitle = "অ্যাকাউন্ট থেকে লগআউট করো",
                    onClick = onLogout,
                    isDestructive = true
                )
            }

            // App Version Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Shikho App v6.0.5",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun sendTestPushNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "shikho_push_notifications"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Shikho Course Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Live class and course updates notifications"
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Shikho - টেস্ট পুশ নোটিফিকেশন 🔔")
        .setContentText("আপনার কোর্স নোটিফিকেশন সাবস্ক্রিপশন সক্রিয় আছে!")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText("আপনার কোর্সের নোটিফিকেশন সাবস্ক্রিপশন সফলভাবে সক্রিয় রয়েছে! লাইভ ক্লাস শুরু হওয়ার সময়ে এবং কোর্সের গুরুত্বপূর্ণ আপডেটের সাথে সাথে সরাসরি আপনার মোবাইলে পুশ নোটিফিকেশন চলে আসবে।")
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    val notificationId = (System.currentTimeMillis() % 10000).toInt()
    notificationManager.notify(notificationId, notification)

    Toast.makeText(context, "টেস্ট পুশ নোটিফিকেশন পাঠানো হয়েছে! 🔔", Toast.LENGTH_LONG).show()
}
