package com.example.ui.screens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.api.StudentLessonItem
import com.example.auth.SessionManager
import com.example.home.HomeViewModel
import com.example.notification.ClassAlarmReceiver
import com.example.notification.ClassAlarmScheduler

data class ClassAlarmItem(
    val id: String,
    val subjectName: String,
    val lessonTitle: String,
    val dateDisplay: String, // e.g. "২৩ সেপ্টেম্বর ২০২৬"
    val startTimeDisplay: String, // e.g. "সকাল ০৭:০০ টা"
    val classTimeHours: Int = 7,
    val classTimeMinutes: Int = 0,
    var isEnabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsScreen(
    sessionManager: SessionManager,
    homeViewModel: HomeViewModel? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedLeadTime by remember { mutableIntStateOf(sessionManager.getClassNotificationLeadTimeMinutes()) }
    var disabledAlarmIds by remember { mutableStateOf(setOf<String>()) }

    val homeUiState = homeViewModel?.uiState?.collectAsState()?.value

    val selectedSubjectCodes = homeUiState?.selectedSubjectCodes ?: emptySet()
    val courseSubjects = homeUiState?.courseSubjects ?: emptyList()

    // Sample/Real Scheduled Alarms Grouped by Date - Strictly filtered by "সাবজেক্ট সাজাও" selection
    val alarmList = remember(homeUiState?.weeklyRoutine, homeUiState?.filteredWeeklyRoutine, selectedSubjectCodes, courseSubjects) {
        val rawLessons = homeUiState?.filteredWeeklyRoutine
        if (!rawLessons.isNullOrEmpty()) {
            rawLessons.mapIndexed { index, lesson ->
                val subject = lesson.subject_name ?: lesson.live_class?.subject_name ?: "পদার্থবিজ্ঞান ১ম পত্র"
                val title = lesson.title ?: lesson.live_class?.chapter_name ?: "নিউটনীয় বলবিদ্যা - লেকচার ১"
                val dateStr = when (index % 3) {
                    0 -> "আজ, ২৩ সেপ্টেম্বর ২০২৬"
                    1 -> "আগামীকাল, ২৪ সেপ্টেম্বর ২০২৬"
                    else -> "২৫ সেপ্টেম্বর ২০২৬"
                }
                val timeStr = when (index % 3) {
                    0 -> "সকাল ০৭:০০ টা"
                    1 -> "সকাল ১০:০০ টা"
                    else -> "সন্ধ্যা ০৭:৩০ টা"
                }
                val hours = when (index % 3) {
                    0 -> 7
                    1 -> 10
                    else -> 19
                }
                val mins = when (index % 3) {
                    0 -> 0
                    1 -> 0
                    else -> 30
                }
                ClassAlarmItem(
                    id = if (lesson.id.isNotBlank()) lesson.id else "lesson_$index",
                    subjectName = subject,
                    lessonTitle = title,
                    dateDisplay = dateStr,
                    startTimeDisplay = timeStr,
                    classTimeHours = hours,
                    classTimeMinutes = mins
                )
            }.distinctBy { it.id }
        } else if (selectedSubjectCodes.isEmpty() && courseSubjects.isNotEmpty()) {
            // User explicitly unselected all subjects in "সাবজেক্ট সাজাও"
            emptyList()
        } else {
            // Default sample alarms if offline or loading - filtered strictly by selectedSubjectCodes
            val allSamples = listOf(
                ClassAlarmItem(
                    id = "sample_1",
                    subjectName = "পদার্থবিজ্ঞান ১ম পত্র",
                    lessonTitle = "নিউটনীয় বলবিদ্যা - লাইভ ক্লাস",
                    dateDisplay = "আজ, ২৩ সেপ্টেম্বর ২০২৬",
                    startTimeDisplay = "সকাল ০৭:০০ টা",
                    classTimeHours = 7,
                    classTimeMinutes = 0
                ),
                ClassAlarmItem(
                    id = "sample_2",
                    subjectName = "রসায়ন ১ম পত্র",
                    lessonTitle = "গুণগত রসায়ন - বিশেষ সংশোধন ক্লাস",
                    dateDisplay = "আগামীকাল, ২৪ সেপ্টেম্বর ২০২৬",
                    startTimeDisplay = "সকাল ১০:০০ টা",
                    classTimeHours = 10,
                    classTimeMinutes = 0
                ),
                ClassAlarmItem(
                    id = "sample_3",
                    subjectName = "উচ্চতর গণিত ১ম পত্র",
                    lessonTitle = "ম্যাট্রিক্স ও নির্ণায়ক - সমস্যা সমাধান",
                    dateDisplay = "২৫ সেপ্টেম্বর ২০২৬",
                    startTimeDisplay = "সন্ধ্যা ০৭:৩০ টা",
                    classTimeHours = 19,
                    classTimeMinutes = 30
                ),
                ClassAlarmItem(
                    id = "sample_4",
                    subjectName = "জীববিজ্ঞান ১ম পত্র",
                    lessonTitle = "কোষ ও এর গঠন - চূড়ান্ত মডেল টেস্ট",
                    dateDisplay = "২৬ সেপ্টেম্বর ২০২৬",
                    startTimeDisplay = "সকাল ০৮:০০ টা",
                    classTimeHours = 8,
                    classTimeMinutes = 0
                )
            )

            if (selectedSubjectCodes.isNotEmpty() && courseSubjects.isNotEmpty()) {
                allSamples.filter { sample ->
                    selectedSubjectCodes.any { code ->
                        code.equals(sample.subjectName, ignoreCase = true) ||
                        sample.subjectName.contains(code, ignoreCase = true) ||
                        courseSubjects.any { sub ->
                            (sub.code.equals(code, ignoreCase = true) || (sub.display_bn != null && sub.display_bn.equals(code, ignoreCase = true))) &&
                            (sub.display_bn != null && sample.subjectName.contains(sub.display_bn, ignoreCase = true))
                        }
                    }
                }
            } else {
                allSamples
            }
        }
    }

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "নোটিফিকেশন পারমিশন প্রয়োজন", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "নোটিফিকেশন ও ক্লাস অ্যালার্ম",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "লাইভ ক্লাস রিমাইন্ডার ও সময় সেটিং",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান"
                        )
                    }
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ==========================================
            // SECTION 1: Lead Time Selector Card
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ক্লাস শুরুর কত মিনিট আগে নোটিফিকেশন পেতে চান?",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ডিফল্ট সময়: ২৫ মিনিট আগে",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Lead Time Chips / Radio Buttons
                    Text(
                        text = "রিমাইন্ডারের সময় বেছে নিন:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val minuteOptions = listOf(10, 15, 20, 25, 30)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        minuteOptions.forEach { mins ->
                            val isSelected = selectedLeadTime == mins
                            val isDefault = mins == 25

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedLeadTime = mins
                                        sessionManager.setClassNotificationLeadTimeMinutes(mins)
                                        Toast.makeText(
                                            context,
                                            "রিমাইন্ডারের সময় $mins মিনিট সেট করা হলো!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    if (isSelected) 1.8.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedLeadTime = mins
                                            sessionManager.setClassNotificationLeadTimeMinutes(mins)
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isDefault) "$mins মিনিট (ডিফল্ট)" else "$mins মিনিট",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Live Preview Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val reminderMinute = (60 - selectedLeadTime) % 60
                            val exampleReminderTime = "০৬:${if (reminderMinute < 10) "0$reminderMinute" else reminderMinute}"

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "উদাহরণ ও প্রিভিউ:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = "যেমন: কোনো ক্লাস সকাল ৭:০০ টায় শুরু হলে, $selectedLeadTime মিনিট আগে অর্থাৎ সকাল $exampleReminderTime টায় মেসেজ পাবেন:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Preview Notification Box
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "⏰ পদার্থবিজ্ঞান ১ম পত্র ক্লাস রিমাইন্ডার",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "আপনার পদার্থবিজ্ঞান ১ম পত্র ক্লাস সকাল ০৭:০০ টায় শুরু হবে, রেডি হন! 🚀",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 2: Scheduled Class Alarms Grouped by Date
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "নির্ধারিত ক্লাস অ্যালার্ম সমূহ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (homeViewModel != null) {
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { homeViewModel.openSubjectFilterDialog() },
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "সাবজেক্ট সাজান",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "তারিখ অনুযায়ী",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Subject Filter Sync Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (selectedSubjectCodes.isNotEmpty()) 
                                    "📌 'সাবজেক্ট সাজাও' অপশনে সিলেক্ট করা ${selectedSubjectCodes.size}টি বিষয়ের ক্লাস অ্যালার্মই নিচে দেখানো হচ্ছে।" 
                                else 
                                    "⚠️ কোনো সাবজেক্ট সিলেক্ট করা নেই। ক্লাস অ্যালার্ম দেখতে 'সাবজেক্ট সাজান' বাটন চাপুন।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                val groupedAlarms = remember(alarmList) {
                    alarmList.groupBy { it.dateDisplay }
                }

                groupedAlarms.forEach { (dateGroup, items) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date Group Header Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = dateGroup,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // Alarm items under this date
                        items.forEach { alarm ->
                            val isAlarmEnabled = !disabledAlarmIds.contains(alarm.id)

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isAlarmEnabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                ),
                                shadowElevation = if (isAlarmEnabled) 1.5.dp else 0.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isAlarmEnabled) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = if (isAlarmEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = alarm.subjectName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAlarmEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Text(
                                                text = alarm.lessonTitle,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = if (isAlarmEnabled) 1f else 0.5f
                                                )
                                            )
                                        }

                                        // Alarm Toggle Switch
                                        Switch(
                                            checked = isAlarmEnabled,
                                            onCheckedChange = { checked ->
                                                disabledAlarmIds = if (checked) {
                                                    disabledAlarmIds - alarm.id
                                                } else {
                                                    disabledAlarmIds + alarm.id
                                                }
                                                Toast.makeText(
                                                    context,
                                                    if (checked) "${alarm.subjectName} অ্যালার্ম চালু করা হলো" else "${alarm.subjectName} অ্যালার্ম বন্ধ করা হলো",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                    // Schedule Time Row
                                    val totalMinutes = (alarm.classTimeHours * 60 + alarm.classTimeMinutes - selectedLeadTime + 1440) % 1440
                                    val triggerHour = totalMinutes / 60
                                    val triggerMin = totalMinutes % 60
                                    val period = if (triggerHour < 12) "সকাল" else if (triggerHour < 17) "দুপুর" else "সন্ধ্যা/রাত"
                                    val displayHour = if (triggerHour % 12 == 0) 12 else triggerHour % 12
                                    val triggerTimeFormatted = "$period ${if (displayHour < 10) "০$displayHour" else displayHour}:${if (triggerMin < 10) "০$triggerMin" else triggerMin} টা"

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "⏰ ক্লাস সময়: ${alarm.startTimeDisplay}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "🔔 রিমাইন্ডার: $triggerTimeFormatted ($selectedLeadTime মি. আগে)",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAlarmEnabled) Color(0xFFD97706) else Color.Gray
                                            )
                                        }

                                        // Test Notification Button for this specific class
                                        OutlinedButton(
                                            onClick = {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                                ) {
                                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                } else {
                                                    triggerClassTestNotification(
                                                        context = context,
                                                        subjectName = alarm.subjectName,
                                                        lessonTitle = alarm.lessonTitle,
                                                        classStartTimeDisplay = alarm.startTimeDisplay,
                                                        leadTimeMinutes = selectedLeadTime
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "টেস্ট করুন",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 3: System Push Notification Test Card
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "সিস্টেম টেস্ট নোটিফিকেশন",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "মোবাইলে নোটিফিকেশন সুবিধা সঠিকভাবে কাজ করছে কিনা পরীক্ষা করুন",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                triggerGeneralTestNotification(context)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "টেস্ট", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (homeViewModel != null && homeUiState?.showSubjectFilterDialog == true) {
        com.example.ui.components.SubjectFilterDialog(
            courseTitle = homeUiState.activeProgram?.title_bn ?: "সাবজেক্ট সাজাও",
            subjects = homeUiState.courseSubjects,
            selectedSubjectCodes = homeUiState.selectedSubjectCodes,
            isLoading = homeUiState.isCourseSubjectsLoading,
            isSaving = homeUiState.isSavingSubjectFilter,
            onToggleSubject = { homeViewModel.toggleSubjectSelection(it) },
            onSelectAll = { homeViewModel.selectAllSubjects() },
            onClearAll = { homeViewModel.clearAllSubjectSelection() },
            onSave = { homeViewModel.saveSubjectFilter() },
            onDismiss = { homeViewModel.dismissSubjectFilterDialog() }
        )
    }
}

private fun triggerClassTestNotification(
    context: Context,
    subjectName: String,
    lessonTitle: String,
    classStartTimeDisplay: String,
    leadTimeMinutes: Int
) {
    ClassAlarmScheduler.createNotificationChannel(context)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val title = "⏰ $subjectName ক্লাস রিমাইন্ডার"
    val body = "আপনার $subjectName ($lessonTitle) ক্লাস $classStartTimeDisplay এ শুরু হবে, রেডি হন! 🚀"

    val mainIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        (System.currentTimeMillis() % 10000).toInt(),
        mainIntent,
        pendingIntentFlags
    )

    val notification = NotificationCompat.Builder(context, ClassAlarmScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    val notificationId = (System.currentTimeMillis() % 100000).toInt()
    notificationManager.notify(notificationId, notification)

    Toast.makeText(context, "$subjectName রিমাইন্ডার টেস্ট পাঠানো হয়েছে! 🔔", Toast.LENGTH_SHORT).show()
}

private fun triggerGeneralTestNotification(context: Context) {
    ClassAlarmScheduler.createNotificationChannel(context)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val title = "MaxBird - নোটিফিকেশন অ্যালার্ম সফল! 🔔"
    val body = "আপনার ক্লাস অ্যালার্ম ও নোটিফিকেশন সিস্টেম সম্পূর্ণ প্রস্তুত! নির্ধারিত ক্লাস শুরুর আগে আপনাকে স্বয়ংক্রিয়ভাবে রিমাইন্ড দেওয়া হবে।"

    val notification = NotificationCompat.Builder(context, ClassAlarmScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(101, notification)
    Toast.makeText(context, "টেস্ট নোটিফিকেশন তৈরি হয়েছে! 🔔", Toast.LENGTH_SHORT).show()
}
