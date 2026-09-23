package com.example.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Rational
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.api.LessonAttachmentItem
import com.example.api.StudentLessonItem
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.player.ShikhoPlayerManager
import com.example.player.HmsLiveSocketManager
import com.example.player.PlayerClassType
import com.example.player.VideoTrackQuality
import com.example.ui.components.*
import com.example.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow

@OptIn(UnstableApi::class)
@Composable
fun LessonDetailPlayerScreen(
    lesson: StudentLessonItem?,
    subjectName: String,
    subjectColorHex: String?,
    isLessonLoading: Boolean = false,
    socketManager: HmsLiveSocketManager? = null,
    onRefreshLesson: (() -> Unit)? = null,
    onJoinLiveClass: ((StudentLessonItem) -> Unit)? = null,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val isExamLesson = lesson?.isExam == true ||
            lesson?.content_type?.contains("EXAM", ignoreCase = true) == true ||
            lesson?.class_type?.contains("EXAM", ignoreCase = true) == true

    if (isExamLesson) {
        val sessionId = lesson?.session_id?.takeIf { it.isNotBlank() }
            ?: lesson?.live_class?.session_id?.takeIf { it.isNotBlank() }
            ?: lesson?.content_id?.takeIf { it.isNotBlank() }
            ?: lesson?.id ?: ""
        val formattedTitle = ClassTypeUtils.formatLessonTitle(lesson?.title ?: "পরীক্ষা")
        val chapterName = lesson?.subject_name ?: subjectName

        LaunchedEffect(sessionId) {
            if (onNavigateToExam != null && sessionId.isNotBlank()) {
                onNavigateToExam(sessionId, lesson?.id ?: "", formattedTitle, chapterName)
            }
        }

        ExamRedirectScreen(
            title = formattedTitle,
            subjectName = chapterName,
            onStartExam = {
                if (onNavigateToExam != null && sessionId.isNotBlank()) {
                    onNavigateToExam(sessionId, lesson?.id ?: "", formattedTitle, chapterName)
                }
            },
            onBack = onBack
        )
        return
    }

    if (lesson?.isUpcoming == true) {
        UpcomingCountdownScreen(
            lesson = lesson,
            subjectName = subjectName,
            subjectColorHex = subjectColorHex,
            onBack = onBack
        )
        return
    }

    val context = LocalContext.current
    val sessionManager = remember(context) { com.example.auth.SessionManager(context) }

    LaunchedEffect(lesson?.id, lesson?.content_id) {
        val lId = lesson?.id ?: ""
        val cId = lesson?.content_id ?: ""
        if (lId.isNotBlank()) sessionManager.markLessonCompleted(lId)
        if (cId.isNotBlank()) sessionManager.markLessonCompleted(cId)
        if (!lesson?.live_class?.id.isNullOrBlank()) sessionManager.markLessonCompleted(lesson!!.live_class!!.id)
    }
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live Class Detection & Info
    val isLive = remember(lesson) {
        lesson?.isLive == true
    }
    val isLiveOngoing = remember(lesson) {
        lesson?.live_class?.is_on_going == true || lesson?.content_type?.contains("LIVE", ignoreCase = true) == true
    }
    val joinLink = lesson?.live_class?.join_link
    val hmsRoomId = lesson?.live_class?.hms_room_id
    val liveProvider = lesson?.live_class?.provider ?: "100ms Live"

    val effectiveMeetingUrl = remember(lesson?.live_class?.liveMeetingUrl, joinLink, hmsRoomId, lesson?.live_class?.hms_token, lesson?.id) {
        val classId = lesson?.live_class?.id ?: lesson?.content_id ?: lesson?.id ?: ""
        val lessonId = lesson?.id ?: ""

        when {
            // ১. Shikho সার্ভার থেকে সরাসরি পাওয়া আসল join_link
            !joinLink.isNullOrBlank() && !joinLink.contains("live.shikho.com") -> {
                val separator = if (joinLink.contains("?")) "&" else "?"
                if (!lesson?.live_class?.hms_token.isNullOrBlank() && !joinLink.contains("token=")) {
                    "$joinLink${separator}token=${lesson.live_class.hms_token}"
                } else {
                    joinLink
                }
            }
            // ২. Shikho-র আসল ওয়েব লাইভ ক্লাস পেজ (যেখানে লাইভ ফ্রন্টএন্ড রান হয়)
            classId.isNotBlank() -> {
                "https://app.shikho.com/student/live-class/$classId?lesson_id=$lessonId"
            }
            // ৩. 100ms রুম আইডি থাকলে অফিসিয়াল 100ms মিটিং লিংক
            !hmsRoomId.isNullOrBlank() -> {
                val tokenParam = if (!lesson?.live_class?.hms_token.isNullOrBlank()) "?token=${lesson.live_class.hms_token}" else ""
                "https://app.100ms.live/meeting/${hmsRoomId.trim()}$tokenParam"
            }
            else -> ""
        }
    }

    // Live Join State
    var isLiveJoined by remember(lesson?.id) { mutableStateOf(true) }

    // Auto trigger joinLiveClass if it's a live class and joinLink or hmsRoomId is missing
    LaunchedEffect(lesson?.id, isLive) {
        if (isLive && lesson != null && (lesson.live_class?.join_link.isNullOrBlank() || lesson.live_class?.hms_room_id.isNullOrBlank()) && onJoinLiveClass != null) {
            onJoinLiveClass.invoke(lesson)
        }
    }

    // Auto bypass LiveGetStartedScreen directly into live stream
    if (isLive && !isLiveJoined) {
        isLiveJoined = true
    }

    var livePlayerMode by remember(isLive) { mutableStateOf("STREAM") }

    val effectiveSocketManager = socketManager ?: remember { HmsLiveSocketManager() }
    val viewerCount by effectiveSocketManager.viewerCount.collectAsState()
    val isHandRaised by effectiveSocketManager.isHandRaised.collectAsState()
    val pinnedMessage by effectiveSocketManager.pinnedMessage.collectAsState()
    val activePoll by effectiveSocketManager.activePoll.collectAsState()

    // Parse Subject Color
    val subjectThemeColor = remember(subjectColorHex) {
        if (!subjectColorHex.isNullOrBlank()) {
            try {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } catch (e: Exception) {
                Color(0xFF2563EB)
            }
        } else {
            Color(0xFF2563EB)
        }
    }

    val candidateStreams = remember(lesson, lesson?.live_class?.hms_room_id, lesson?.live_class?.recording_url, lesson?.live_class?.playback_url, isLive) {
        val raw = lesson?.candidateStreamUrls?.filter { it.isNotBlank() && it != "null" } ?: emptyList()
        if (isLive) {
            val liveHls = raw.filter { it.contains("100ms.live") }
            val other = raw.filterNot { it.contains("100ms.live") }
            (liveHls + other).distinct()
        } else {
            raw.distinct()
        }
    }
    var currentStreamIndex by remember(lesson?.id, lesson?.live_class?.hms_room_id) { mutableIntStateOf(0) }
    var activeStreamUrl by remember(candidateStreams, currentStreamIndex) {
        mutableStateOf(
            candidateStreams.getOrNull(currentStreamIndex)
                ?: candidateStreams.firstOrNull()
                ?: lesson?.resolvedVideoUrl
                ?: ""
        )
    }

    // Resolve class type: Animated vs Recorded Lecture vs Live
    val classType = remember(lesson, activeStreamUrl, isLive) {
        PlayerClassType.resolve(
            isLive = isLive,
            contentType = lesson?.content_type,
            classType = lesson?.class_type ?: lesson?.live_class?.class_type,
            title = lesson?.title,
            url = activeStreamUrl
        )
    }

    // Connect 100ms WebSocket when Live Class is active with valid HMS token
    LaunchedEffect(isLive, lesson?.live_class?.hms_token, lesson?.live_class?.hms_room_id) {
        val token = lesson?.live_class?.hms_token
        val roomId = lesson?.live_class?.hms_room_id
        if (isLive && !token.isNullOrBlank()) {
            effectiveSocketManager.connect(token, roomId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            effectiveSocketManager.disconnect()
        }
    }

    LaunchedEffect(candidateStreams) {
        if (candidateStreams.isNotEmpty()) {
            if (activeStreamUrl.isBlank() || !candidateStreams.contains(activeStreamUrl)) {
                currentStreamIndex = 0
                activeStreamUrl = candidateStreams.first()
            }
        }
    }

    // Diagnostics & Dialog States
    var playbackError by remember { mutableStateOf<String?>(null) }
    var playbackErrorDetails by remember { mutableStateOf<String?>(null) }

    var showCustomUrlDialog = false

    // Player States
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var availableQualities by remember { mutableStateOf<List<VideoTrackQuality>>(emptyList()) }
    var selectedQualityLabel by remember { mutableStateOf("অটো") }

    val toggleResizeMode: () -> Unit = {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val modeName = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "ভিডিও সাইজ: ফিট স্ক্রিন (১৬:৯)"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ভিডিও সাইজ: জুম ও ফিল স্ক্রিন"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ভিডিও সাইজ: ফুল স্ক্রিন স্ট্রেচ"
            else -> "ফিট স্ক্রিন"
        }
        Toast.makeText(context, modeName, Toast.LENGTH_SHORT).show()
    }

    // Slide viewing state
    var viewingSlideItem by remember { mutableStateOf<LessonAttachmentItem?>(null) }
    var isRefreshingSlide by remember { mutableStateOf(false) }

    // Expandable Accordion State for Topics
    var isTopicsExpanded by remember { mutableStateOf(true) }

    // Download Manager & Video Offline Caching
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    val videoDownloadId = remember(lesson?.id, activeStreamUrl) {
        "vid_" + ((lesson?.id ?: activeStreamUrl.ifBlank { "lesson" }).hashCode().toString() + "_" + (lesson?.title ?: "").hashCode().toString()).replace("-", "n")
    }
    val videoDownloadedItem by downloadManager.getDownloadedItemById(videoDownloadId).collectAsState(initial = null)

    val handleDownloadVideo: () -> Unit = {
        when (videoDownloadedItem?.status) {
            DownloadedItemEntity.STATUS_DOWNLOADING -> {
                downloadManager.cancelDownload(videoDownloadId)
                Toast.makeText(context, "ভিডিও ডাউনলোড বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
            DownloadedItemEntity.STATUS_COMPLETED -> {
                Toast.makeText(context, "এই ভিডিওটি ইতিমধ্যে অফলাইনে ডাউনলোড করা আছে। 'ডাউনলোড' ট্যাবে দেখতে পাবেন।", Toast.LENGTH_LONG).show()
            }
            else -> {
                val downloadUrl = activeStreamUrl.ifBlank {
                    lesson?.resolvedVideoUrl
                        ?: candidateStreams.firstOrNull()
                        ?: ""
                }
                if (downloadUrl.isNotBlank() && downloadUrl != "null") {
                    showDownloadQualityDialog = true
                } else {
                    Toast.makeText(context, "ভিডিও ডাউনলোড লিংক পাওয়া যায়নি বা লাইভ ক্লাস এখনও চলছে।", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showDownloadQualityDialog) {
        val downloadSourceUrl = activeStreamUrl.ifBlank {
            lesson?.resolvedVideoUrl
                ?: candidateStreams.firstOrNull()
                ?: ""
        }
        VideoDownloadQualityDialog(
            videoUrl = downloadSourceUrl,
            title = lesson?.title ?: "ক্লাস ভিডিও",
            downloadedItem = videoDownloadedItem,
            onDismiss = { showDownloadQualityDialog = false },
            onConfirmDownload = { selectedQuality ->
                showDownloadQualityDialog = false
                downloadManager.downloadFile(
                    id = videoDownloadId,
                    title = lesson?.title ?: "ক্লাস ভিডিও লেকচার",
                    subtitle = subjectName,
                    fileType = DownloadedItemEntity.FILE_TYPE_VIDEO,
                    remoteUrl = selectedQuality.targetM3u8Url
                )
                Toast.makeText(
                    context,
                    "ভিডিও ডাউনলোড শুরু হয়েছে (${selectedQuality.labelBangla})। 'ডাউনলোড' ট্যাবে দেখতে পাবেন।",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // ExoPlayer Instance with Shikho CDN headers & DefaultTrackSelector for HLS quality selection
    val trackSelector = remember { DefaultTrackSelector(context) }
    val exoPlayer = remember(classType) {
        ShikhoPlayerManager.buildExoPlayer(context, trackSelector, classType).apply {
            repeatMode = if (classType == PlayerClassType.ANIMATED) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    // Initialize media source when activeStreamUrl or mode changes
    LaunchedEffect(activeStreamUrl, isLive, livePlayerMode) {
        playbackError = null
        playbackErrorDetails = null
        if (livePlayerMode == "MEETING" || livePlayerMode == "WEB_PLAYER") {
            // When in WebView mode, stop ExoPlayer to prevent background stream fetching
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (_: Exception) {}
            isBuffering = false
            return@LaunchedEffect
        }
        if (activeStreamUrl.isNotBlank()) {
            val urlToPlay = activeStreamUrl

            // If activeStreamUrl is a master.m3u8, ExoPlayer's onTracksChanged will parse tracks dynamically.
            // If it's a direct stream_X URL, fallback to pre-populating availableQualities.
            if (urlToPlay.contains("/stream_")) {
                val baseUrl = urlToPlay
                val s0 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_0/stream.m3u8")
                val s1 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_1/stream.m3u8")
                val s2 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_2/stream.m3u8")
                val s3 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_3/stream.m3u8")

                availableQualities = listOf(
                    VideoTrackQuality("auto", "অটো (Auto)", 0, 0, null, 0, s0),
                    VideoTrackQuality("1080p", "1080p (উচ্চ মান)", 1080, 0, null, 0, s0),
                    VideoTrackQuality("720p", "720p (এইচডি)", 720, 0, null, 0, s1),
                    VideoTrackQuality("480p", "480p (মাঝারি)", 480, 0, null, 0, s2),
                    VideoTrackQuality("360p", "360p (সাধারণ)", 360, 0, null, 0, s3)
                )
            }

            isBuffering = true
            try {
                val mediaSource = ShikhoPlayerManager.createMediaSource(urlToPlay, isLive = isLive)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                isBuffering = false
                playbackError = "প্লেয়ার প্রস্তুত করতে ব্যর্থ"
                playbackErrorDetails = e.localizedMessage ?: "অজানা ত্রুটি"
            }
        } else {
            isBuffering = false
        }
    }

    // Player Event Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                val qualities = mutableListOf<VideoTrackQuality>()
                qualities.add(VideoTrackQuality(id = "auto", label = "অটো (Auto)", height = 0, bitrate = 0))

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val height = format.height
                            val bitrate = format.bitrate
                            if (height > 0) {
                                val label = "${height}p" + if (bitrate > 0) " (${bitrate / 1000} kbps)" else ""
                                qualities.add(
                                    VideoTrackQuality(
                                        id = "${height}_${bitrate}",
                                        label = label,
                                        height = height,
                                        bitrate = bitrate,
                                        trackGroup = group,
                                        trackIndex = i
                                    )
                                )
                            }
                        }
                    }
                }
                availableQualities = qualities.distinctBy { it.label }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        playbackError = null
                        playbackErrorDetails = null
                        totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        isBuffering = false
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (livePlayerMode == "MEETING") {
                    // Do not process or display ExoPlayer errors when user is in 100ms MEETING WebView
                    isBuffering = false
                    isPlaying = false
                    return
                }

                if (currentStreamIndex + 1 < candidateStreams.size) {
                    currentStreamIndex += 1
                    // Updating currentStreamIndex automatically changes activeStreamUrl, which triggers LaunchedEffect
                    return
                }

                isBuffering = false
                isPlaying = false
                val cause = error.cause
                val httpEx = cause as? HttpDataSource.InvalidResponseCodeException
                    ?: (cause?.cause as? HttpDataSource.InvalidResponseCodeException)
                val httpCode = httpEx?.responseCode

                val detail = when {
                    httpCode == 404 -> "ভিডিও ফাইলটি সার্ভারে পাওয়া যায়নি (HTTP 404)"
                    httpCode == 403 -> "CDN অ্যাক্সেস রিজেক্টেড (HTTP 403)"
                    httpCode != null -> "CDN নেটওয়ার্ক রেসপন্স ত্রুটি (HTTP $httpCode)"
                    cause is java.net.UnknownHostException -> "ইন্টারনেট সংযোগ নেই বা CDN সার্ভারে পৌঁছানো যাচ্ছে না"
                    cause is java.net.SocketTimeoutException -> "সার্ভার সংযোগ সময়োত্তীর্ণ (Connection Timeout)"
                    else -> error.localizedMessage ?: "অজানা প্লেব্যাক ত্রুটি"
                }
                playbackError = "প্লেব্যাক এরর"
                playbackErrorDetails = detail
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic Progress Tracking Loop
    LaunchedEffect(isPlaying, isSeeking) {
        while (isPlaying && !isSeeking) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Controls Auto-Hide Timer
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying && !isSeeking) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // Lifecycle Observer (Pause on background, Resume on foreground)

    val mediaSession = remember(exoPlayer) {
        try {
            MediaSession.Builder(context, exoPlayer)
                .setId("session_ldp_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}")
                .build()
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(mediaSession) {
        onDispose {
            try {
                mediaSession?.release()
            } catch (_: Exception) {}
        }
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val activity = context as? Activity
                    if (activity?.isInPictureInPictureMode != true) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying) exoPlayer.play()
                }
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isPipMode = com.example.LocalPictureInPictureMode.current
    val configuration = LocalConfiguration.current

    // Handle device physical orientation rotation dynamically
    LaunchedEffect(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !isFullscreen) {
            isFullscreen = true
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT && isFullscreen) {
            isFullscreen = false
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity?.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(context, "PiP মোড চালু করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "আপনার ডিভাইসে PiP মোড সমর্থিত নয়", Toast.LENGTH_SHORT).show()
        }
    }

    // Fullscreen Screen Orientation & Immersive Sticky System Bars
    fun toggleFullscreen() {
        val newFullscreen = !isFullscreen
        isFullscreen = newFullscreen
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (newFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Back Handler to exit fullscreen first if active
    BackHandler {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            exoPlayer.stop()
            onBack()
        }
    }

    // Clean up screen orientation on leaving
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                if (act.isFinishing) {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    val window = act.window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    // Picture In Picture View Mode
    if (isPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv ->
                    pv.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    // UI Structure
    if (isFullscreen) {
        // FULLSCREEN VIDEO PLAYER / MEETING
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isLive && effectiveMeetingUrl.isNotBlank()) {
                LiveMeetingWebView(
                    meetingUrl = effectiveMeetingUrl,
                    studentName = sessionManager.getUserFirstName() ?: "Student",
                    authToken = sessionManager.getAccessToken(),
                    onStreamDiscovered = { discoveredM3u8 ->
                        activeStreamUrl = discoveredM3u8
                        livePlayerMode = "STREAM"
                    },
                    modifier = if (activeStreamUrl.isNotBlank() && livePlayerMode == "STREAM") Modifier.size(1.dp) else Modifier.fillMaxSize()
                )
            }
            if (activeStreamUrl.isNotBlank() || (!isLive && candidateStreams.isNotEmpty())) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            this.resizeMode = resizeMode
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { pv ->
                        pv.player = exoPlayer
                        pv.resizeMode = resizeMode
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Fullscreen Player Controls Overlay
                val seekStep = if (classType == PlayerClassType.ANIMATED) 5000L else 10000L
                PlayerControlsOverlay(
                    title = lesson?.title ?: "রেকর্ডকৃত ক্লাস",
                    subjectName = subjectName,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    currentPosition = if (isSeeking) seekPosition else currentPosition,
                    bufferedPosition = bufferedPosition,
                    totalDuration = totalDuration,
                    areControlsVisible = areControlsVisible,
                    isFullscreen = true,
                    playbackSpeed = playbackSpeed,
                    isLive = isLive,
                    viewerCount = if (isLive) viewerCount else null,
                    classType = classType,
                    hasMeeting = effectiveMeetingUrl.isNotBlank(),
                    onSwitchToMeeting = {
                        exoPlayer.pause()
                        livePlayerMode = "MEETING"
                    },
                    onTogglePlayPause = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onSeekBack = {
                        val target = (exoPlayer.currentPosition - seekStep).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                    },
                    onSeekForward = {
                        val target = (exoPlayer.currentPosition + seekStep).coerceAtMost(totalDuration)
                        exoPlayer.seekTo(target)
                    },
                    onSeekStarted = {
                        isSeeking = true
                        seekPosition = it
                    },
                    onSeekChanged = {
                        seekPosition = it
                    },
                    onSeekFinished = { targetPos ->
                        currentPosition = targetPos
                        seekPosition = targetPos
                        exoPlayer.seekTo(targetPos)
                        coroutineScope.launch {
                            delay(350)
                            isSeeking = false
                        }
                    },
                    onToggleFullscreen = { toggleFullscreen() },
                    onToggleControls = { areControlsVisible = !areControlsVisible },
                    onSpeedClick = { showSpeedDialog = true },
                    onQualityClick = { showQualityDialog = true },
                    selectedQualityLabel = selectedQualityLabel,
                    downloadedItem = videoDownloadedItem,
                    onDownloadClick = handleDownloadVideo,
                    resizeMode = resizeMode,
                    onToggleResizeMode = toggleResizeMode,
                    onPipClick = { enterPipMode() },
                    onBack = { toggleFullscreen() }
                )
            } else if (isLessonLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ক্লাস লোড হচ্ছে...",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    } else {
        // PORTRAIT DETAIL SCREEN
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Top Video Player (16:9 Aspect Ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (isLive && effectiveMeetingUrl.isNotBlank()) {
                        LiveMeetingWebView(
                            meetingUrl = effectiveMeetingUrl,
                            studentName = sessionManager.getUserFirstName() ?: "Student",
                            authToken = sessionManager.getAccessToken(),
                            onStreamDiscovered = { discoveredM3u8 ->
                                activeStreamUrl = discoveredM3u8
                                livePlayerMode = "STREAM"
                            },
                            modifier = if (activeStreamUrl.isNotBlank() && livePlayerMode == "STREAM") Modifier.size(1.dp) else Modifier.fillMaxSize()
                        )
                    }
                    if (livePlayerMode == "WEB_PLAYER") {
                        val webStreamUrl = activeStreamUrl
                        if (webStreamUrl.isNotBlank()) {
                            HlsWebPlayerView(
                                streamUrl = webStreamUrl,
                                onBackToStream = { livePlayerMode = "STREAM" },
                                onOpenExternal = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webStreamUrl)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (activeStreamUrl.isNotBlank() || (!isLive && candidateStreams.isNotEmpty())) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    this.resizeMode = resizeMode
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            update = { pv ->
                                pv.player = exoPlayer
                                pv.resizeMode = resizeMode
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // If playbackError is present, show a sleek diagnostic overlay on top of the player!
                        if (playbackError != null) {
                            val slideUrlForError = lesson?.resolvedSlideUrl
                                ?: lesson?.live_class?.lectureSlideUrl
                            PlayerErrorOverlay(
                                playbackError = playbackError ?: "ক্লাস লোড ব্যর্থ হয়েছে",
                                playbackErrorDetails = playbackErrorDetails,
                                isLive = isLive,
                                slideUrl = slideUrlForError,
                                onRefreshLesson = onRefreshLesson,
                                onRetryPlayback = {
                                    val curr = activeStreamUrl
                                    activeStreamUrl = ""
                                    coroutineScope.launch {
                                        delay(150)
                                        activeStreamUrl = curr
                                    }
                                },
                                onLaunchWebPlayer = { livePlayerMode = "WEB_PLAYER" },
                                onViewSlide = { item -> viewingSlideItem = item },
                                onBack = onBack
                            )
                        } else {
                            // Player Controls Overlay
                            val seekStep = if (classType == PlayerClassType.ANIMATED) 5000L else 10000L
                            PlayerControlsOverlay(
                                title = lesson?.title ?: "ক্লাস",
                                subjectName = subjectName,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                currentPosition = if (isSeeking) seekPosition else currentPosition,
                                bufferedPosition = bufferedPosition,
                                totalDuration = totalDuration,
                                areControlsVisible = areControlsVisible,
                                isFullscreen = false,
                                playbackSpeed = playbackSpeed,
                                isLive = isLive,
                                viewerCount = if (isLive) viewerCount else null,
                                classType = classType,
                                hasMeeting = effectiveMeetingUrl.isNotBlank(),
                                onSwitchToMeeting = {
                                    exoPlayer.pause()
                                    livePlayerMode = "MEETING"
                                },
                                onTogglePlayPause = {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                onSeekBack = {
                                    val target = (exoPlayer.currentPosition - seekStep).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                },
                                onSeekForward = {
                                    val target = (exoPlayer.currentPosition + seekStep).coerceAtMost(totalDuration)
                                    exoPlayer.seekTo(target)
                                },
                                onSeekStarted = {
                                    isSeeking = true
                                    seekPosition = it
                                },
                                onSeekChanged = {
                                    seekPosition = it
                                },
                                onSeekFinished = { targetPos ->
                                    currentPosition = targetPos
                                    seekPosition = targetPos
                                    exoPlayer.seekTo(targetPos)
                                    coroutineScope.launch {
                                        delay(350)
                                        isSeeking = false
                                    }
                                },
                                onToggleFullscreen = { toggleFullscreen() },
                                onToggleControls = { areControlsVisible = !areControlsVisible },
                                onSpeedClick = { showSpeedDialog = true },
                                onQualityClick = { showQualityDialog = true },
                                selectedQualityLabel = selectedQualityLabel,
                                downloadedItem = videoDownloadedItem,
                                onDownloadClick = handleDownloadVideo,
                                resizeMode = resizeMode,
                                onToggleResizeMode = toggleResizeMode,
                                onPipClick = { enterPipMode() },
                                onBack = {
                                    exoPlayer.stop()
                                    onBack()
                                }
                            )
                        }
                    } else if (isLessonLoading) {
                        // Actively fetching stream and materials from server/database
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(20.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.5.dp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "ভিডিও ও স্টাডি মেটেরিয়াল লোড করা হচ্ছে...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "অনুগ্রহ করে কিছুক্ষণ অপেক্ষা করুন",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else if (!isLive) {
                        // Empty / No Direct Stream State Placeholder with Diagnostics
                        val slideUrlForEmpty = lesson?.resolvedSlideUrl
                            ?: lesson?.live_class?.lectureSlideUrl
                        LessonStreamPlaceholder(
                            isLive = false,
                            lesson = lesson,
                            slideUrl = slideUrlForEmpty,
                            onJoinLiveClass = onJoinLiveClass,
                            onRefreshLesson = onRefreshLesson,
                            onViewSlide = { item -> viewingSlideItem = item },
                            onBack = onBack
                        )
                    }
                }

                // Live Class Active Room Banner & Control Card (if live)
                if (isLive) {
                    LiveClassRoomBanner(
                        isLiveOngoing = isLiveOngoing,
                        liveProvider = liveProvider,
                        hmsRoomId = hmsRoomId,
                        livePlayerMode = livePlayerMode,
                        effectiveMeetingUrl = effectiveMeetingUrl,
                        onTogglePlayerMode = {
                            if (livePlayerMode == "MEETING") {
                                livePlayerMode = "STREAM"
                                exoPlayer.play()
                            } else {
                                exoPlayer.pause()
                                livePlayerMode = "MEETING"
                            }
                        },
                        onRefreshLesson = onRefreshLesson
                    )
                }

                // 2. Class Header & Info Section
                LessonDetailHeader(lesson = lesson)

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3. "ক্লাসের বিষয়বস্তু" (Expandable Accordion)
                LessonTopicsAccordion(
                    lesson = lesson,
                    subjectThemeColor = subjectThemeColor,
                    isExpanded = isTopicsExpanded,
                    onToggleExpand = { isTopicsExpanded = !isTopicsExpanded },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 4. "লেকচার স্লাইডস ও ডকুমেন্টস"
                LessonDocumentsSection(
                    lesson = lesson,
                    context = context,
                    coroutineScope = coroutineScope,
                    isLoading = isLessonLoading,
                    onRefreshLesson = onRefreshLesson,
                    onViewAttachment = { attachment ->
                        viewingSlideItem = attachment
                    }
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Playback Speed Selection Dialog
    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            playbackSpeed = playbackSpeed,
            onSpeedChange = { newSpeed ->
                playbackSpeed = newSpeed
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    // Video Quality Selection Dialog
    if (showQualityDialog) {
        VideoQualityDialog(
            availableQualities = availableQualities,
            selectedQualityLabel = selectedQualityLabel,
            onSelectQuality = { quality ->
                selectedQualityLabel = quality.label
                val parametersBuilder = trackSelector.buildUponParameters()
                if (quality.id == "auto") {
                    parametersBuilder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    trackSelector.setParameters(parametersBuilder)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .build()
                } else if (quality.trackGroup != null) {
                    val override = TrackSelectionOverride(
                        quality.trackGroup.mediaTrackGroup,
                        listOf(quality.trackIndex)
                    )
                    parametersBuilder.setOverrideForType(override)
                    trackSelector.setParameters(parametersBuilder)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(override)
                        .build()
                } else if (quality.targetStreamUrl != null && quality.targetStreamUrl != activeStreamUrl) {
                    activeStreamUrl = quality.targetStreamUrl
                }
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    // In-App Slide Viewer Dialog
    if (viewingSlideItem != null) {
        val slide = viewingSlideItem!!
        val url = slide.downloadUrl ?: ""
        SlideViewerDialog(
            slideUrl = url,
            title = slide.displayTitle,
            onDismiss = { viewingSlideItem = null }
        )
    }
}

@Composable
private fun ExamRedirectScreen(
    title: String,
    subjectName: String,
    onStartExam: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "পরীক্ষা (Exam)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFEF3C7),
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✍️", fontSize = 40.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFEDD5)
            ) {
                Text(
                    text = "লাইভ পরীক্ষা / এক্সাম",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEA580C),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            if (subjectName.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "বিষয়: $subjectName",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "এটি একটি লাইভ পরীক্ষা (Exam), কোনো রেকর্ডেড ক্লাস বা ভিডিও লেকচার নয়। সরাসরি পরীক্ষায় অংশ নিতে নিচের বাটনে চাপ দিন।",
                fontSize = 14.sp,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onStartExam,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072EC)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("✍️ পরীক্ষায় অংশ নিন", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}



