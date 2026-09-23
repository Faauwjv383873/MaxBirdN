package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.database.NotificationHistoryEntity
import com.example.notification.FcmDiagnosticState
import com.example.notification.NotificationHistoryViewModel
import com.example.notification.TopicCategory
import com.example.notification.TopicStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    viewModel: NotificationHistoryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val diagnosticState by viewModel.diagnosticState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(diagnosticState.testNotificationMessage) {
        diagnosticState.testNotificationMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearTestMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("notification_history_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "নোটিফিকেশন",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (uiState.unreadCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${uiState.unreadCount}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = "লাইভ ফায়ারবেস সার্ভিস ও নোটিফিকেশন হিস্ট্রি",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshDiagnostics() },
                        enabled = !diagnosticState.isChecking,
                        modifier = Modifier.testTag("refresh_diagnostics_button")
                    ) {
                        if (diagnosticState.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "কানেকশন পুনরায় টেস্ট করুন"
                            )
                        }
                    }
                    if (uiState.notifications.isNotEmpty()) {
                        if (uiState.unreadCount > 0) {
                            IconButton(
                                onClick = { viewModel.markAllAsRead() },
                                modifier = Modifier.testTag("mark_all_read_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "সব পঠিত চিহ্নিত করুন",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "সব মুছুন",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. LIVE DIAGNOSTIC & FIREBASE STATUS CARD
            item {
                FirebaseDiagnosticCard(
                    state = diagnosticState,
                    onRefresh = { viewModel.refreshDiagnostics() },
                    onSendTestNotification = { viewModel.sendTestNotification() },
                    onCopyToken = { token ->
                        clipboardManager.setText(AnnotatedString(token))
                    },
                    onOpenPermissionSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 2. NOTIFICATION HISTORY HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "সংরক্ষিত নোটিফিকেশন (${uiState.notifications.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 3. NOTIFICATION LIST OR EMPTY STATE
            if (uiState.notifications.isEmpty()) {
                item {
                    EmptyNotificationCard()
                }
            } else {
                items(
                    items = uiState.notifications,
                    key = { it.id }
                ) { item ->
                    NotificationCard(
                        item = item,
                        onClick = { viewModel.markAsClicked(item.id) },
                        onDelete = { viewModel.deleteNotification(item.id) }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("সব নোটিফিকেশন মুছবেন?") },
            text = { Text("আপনার সব সংরক্ষিত নোটিফিকেশন হিস্টোরি মুছে যাবে। আপনি কি নিশ্চিত?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    }
                ) {
                    Text("হ্যাঁ, মুছুন", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
fun FirebaseDiagnosticCard(
    state: FcmDiagnosticState,
    onRefresh: () -> Unit,
    onSendTestNotification: () -> Unit,
    onCopyToken: (String) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isTerminalExpanded by remember { mutableStateOf(false) }
    var tokenCopied by remember { mutableStateOf(false) }

    val isConnected = state.isFirebaseInitialized &&
            state.isNotificationPermissionGranted &&
            !state.fcmToken.isNullOrBlank()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .testTag("firebase_diagnostic_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title + Master Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "লাইভ সার্ভিস স্ট্যাটাস",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        state.isChecking -> Color(0xFFFEF3C7)
                        isConnected -> Color(0xFFD1FAE5)
                        else -> Color(0xFFFEE2E2)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        state.isChecking -> Color(0xFFD97706)
                                        isConnected -> Color(0xFF059669)
                                        else -> Color(0xFFDC2626)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                state.isChecking -> "যাচাই চলছে..."
                                isConnected -> "সংযুক্ত (Connected)"
                                else -> "সমস্যা রয়েছে"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                state.isChecking -> Color(0xFF92400E)
                                isConnected -> Color(0xFF065F46)
                                else -> Color(0xFF991B1B)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Firebase Project Row
            DiagnosticRowItem(
                icon = Icons.Default.CloudQueue,
                label = "Firebase প্রজেক্ট",
                value = if (state.isFirebaseInitialized) {
                    "${state.firebaseProjectId ?: "অজ্ঞাত"} (সক্রিয়)"
                } else "ইনিশিয়ালাইজ হয়নি",
                isSuccess = state.isFirebaseInitialized && state.firebaseProjectId == "shikho-tech"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Notification Permission Row
            DiagnosticRowItem(
                icon = Icons.Default.NotificationsActive,
                label = "নোটিফিকেশন পারমিশন",
                value = if (state.isNotificationPermissionGranted) "অনুমোদিত (Granted)" else "অনুমতি দেওয়া হয়নি (Denied)",
                isSuccess = state.isNotificationPermissionGranted,
                actionText = if (!state.isNotificationPermissionGranted) "সেটিংস খুলুন" else null,
                onAction = onOpenPermissionSettings
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. FCM Registration Token Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = if (!state.fcmToken.isNullOrBlank()) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FCM Token:",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (!state.fcmToken.isNullOrBlank()) {
                            "${state.fcmToken.take(10)}...${state.fcmToken.takeLast(6)}"
                        } else if (state.tokenError != null) {
                            "ব্যর্থ: ${state.tokenError}"
                        } else "অপেক্ষমাণ...",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!state.fcmToken.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            onCopyToken(state.fcmToken)
                            tokenCopied = true
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = if (tokenCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (tokenCopied) "কপিকৃত" else "কপি",
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Active Course & Syllabus Context Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "সক্রিয় কোর্স ও সিলেবাস",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "কোর্স: ${state.activeCourseTitle ?: "নির্বাচন করা হয়নি"} ${if (!state.activeProgramId.isNullOrBlank()) "(ID: ${state.activeProgramId})" else ""}",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "শ্রেণি/সিলেবাস: ${state.academicClass ?: "C11"}",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Dynamic Topic Replacement Notification Info Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEFF6FF),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBFDBFE))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "স্বয়ংক্রিয় টপিক রিপ্লেসমেন্ট সক্রিয়: হোম থেকে কোর্স সুইচ করলে বা সিলেবাস বদলালে পুরানো টপিক আনসাবস্ক্রাইব হয়ে নতুন টপিক সাবস্ক্রাইব হবে।",
                        fontSize = 11.sp,
                        color = Color(0xFF1E40AF),
                        lineHeight = 15.sp
                    )
                }
            }

            if (state.unsubscribedTopics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🔄 পুরানো আনসাবস্ক্রাইবকৃত টপিক: ${state.unsubscribedTopics.joinToString(", ")}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD97706)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Subscribed Topics List
            Text(
                text = "সাবস্ক্রাইব করা টপিকগুলো (${state.topics.size}):",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (state.topics.isEmpty()) {
                Text(
                    text = "কোনো টপিক সাবস্ক্রিপশন সম্পন্ন হয়নি। পুনরায় টেস্ট করুন।",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.topics.forEach { topic ->
                        TopicStatusBadge(topic = topic)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Diagnostic Terminal Log Section
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A), // Dark slate terminal background
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTerminalExpanded = !isTerminalExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Mac-style colored terminal dots
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ডায়াগনস্টিক টার্মিনাল লগ",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Icon(
                            imageVector = if (isTerminalExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isTerminalExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (state.terminalLogs.isEmpty()) {
                                Text(
                                    text = "টার্মিনাল লগ খালি। পুনরায় টেস্ট বাটন প্রেস করুন।",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                state.terminalLogs.forEach { logLine ->
                                    val logColor = when {
                                        logLine.contains("❌") || logLine.contains("ত্রুটি") || logLine.contains("ব্যর্থ") -> Color(0xFFF87171)
                                        logLine.contains("⚠️") || logLine.contains("সতর্ক") -> Color(0xFFFBBF24)
                                        logLine.contains("✅") || logLine.contains("🎉") -> Color(0xFF34D399)
                                        else -> Color(0xFF38BDF8)
                                    }
                                    Text(
                                        text = logLine,
                                        color = logColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Send Test Notification & Re-test
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSendTestNotification,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("send_test_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "টেস্ট নোটিফিকেশন",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !state.isChecking,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("retest_connection_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "পুনরায় টেস্ট",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticRowItem(
    icon: ImageVector,
    label: String,
    value: String,
    isSuccess: Boolean,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$label:",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                fontSize = 12.5.sp,
                color = if (isSuccess) Color(0xFF059669) else Color(0xFFDC2626),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (actionText != null && onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text(text = actionText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun TopicStatusBadge(topic: TopicStatus) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (topic.isSubscribed) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (topic.isSubscribed) Color(0xFFA7F3D0) else Color(0xFFFECACA)
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = topic.topicName,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (topic.isSubscribed) Color(0xFF065F46) else Color(0xFF991B1B)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (topic.category) {
                            TopicCategory.PUBLIC_BROADCAST -> Color(0xFFE0E7FF)
                            TopicCategory.ACTIVE_COURSE -> Color(0xFFFEF3C7)
                            TopicCategory.ACADEMIC_CLASS -> Color(0xFFF3E8FF)
                            TopicCategory.USER_ACCOUNT -> Color(0xFFF1F5F9)
                        }
                    ) {
                        Text(
                            text = topic.category.labelBn,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (topic.category) {
                                TopicCategory.PUBLIC_BROADCAST -> Color(0xFF3730A3)
                                TopicCategory.ACTIVE_COURSE -> Color(0xFF92400E)
                                TopicCategory.ACADEMIC_CLASS -> Color(0xFF6B21A8)
                                TopicCategory.USER_ACCOUNT -> Color(0xFF334155)
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = topic.description,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (topic.error != null) {
                    Text(
                        text = "ত্রুটি: ${topic.error}",
                        fontSize = 10.sp,
                        color = Color(0xFFDC2626)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = if (topic.isSubscribed) Color(0xFF10B981) else Color(0xFFEF4444)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (topic.isSubscribed) "Active (${topic.latencyMs}ms)" else "Failed",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyNotificationCard(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "কোনো নোটিফিকেশন হিস্ট্রি নেই",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "শিখো থেকে কোনো লাইভ ক্লাস বা পরীক্ষার পুশ নোটিফিকেশন আসলে তা এখানে সংরক্ষিত হবে। আপনি উপরের 'টেস্ট নোটিফিকেশন' বাটন চেপে এখনই টেস্ট করে দেখতে পারেন।",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun NotificationCard(
    item: NotificationHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnread = !item.isRead
    val cardBg = if (isUnread) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isUnread) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(borderColor)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnread) 2.dp else 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("notification_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            val iconInfo = getNotificationIcon(item.type, item.title)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconInfo.second.copy(alpha = 0.15f))
                    .border(1.dp, iconInfo.second.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconInfo.first,
                    contentDescription = null,
                    tint = iconInfo.second,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isUnread) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                if (item.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.body,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }

                if (!item.imageUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = "Notification Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatRelativeTime(item.receivedAt),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "মুছুন",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getNotificationIcon(type: String?, title: String): Pair<ImageVector, Color> {
    val lowerTitle = title.lowercase()
    val lowerType = (type ?: "").lowercase()

    return when {
        lowerTitle.contains("লাইভ") || lowerType.contains("live") || lowerTitle.contains("ক্লাস") -> {
            Pair(Icons.Default.LiveTv, Color(0xFFE11D48))
        }
        lowerTitle.contains("পরীক্ষা") || lowerTitle.contains("exam") || lowerTitle.contains("কুইজ") || lowerType.contains("quiz") -> {
            Pair(Icons.Default.Assignment, Color(0xFFF59E0B))
        }
        lowerTitle.contains("রুটিন") || lowerType.contains("routine") -> {
            Pair(Icons.Default.CalendarToday, Color(0xFF0284C7))
        }
        else -> {
            Pair(Icons.Default.Notifications, Color(0xFF6366F1))
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    val oneMinute = 60 * 1000L
    val oneHour = 60 * oneMinute
    val oneDay = 24 * oneHour

    return when {
        diff < oneMinute -> "এইমাত্র"
        diff < oneHour -> {
            val minutes = diff / oneMinute
            "$minutes মিনিট আগে"
        }
        diff < oneDay -> {
            val hours = diff / oneHour
            "$hours ঘণ্টা আগে"
        }
        diff < 2 * oneDay -> "গতকাল"
        else -> {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
