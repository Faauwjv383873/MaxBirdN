package com.example.birdgram.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.birdgram.BirdgramRepository
import com.example.birdgram.TelegramClientVariant
import kotlinx.coroutines.launch

/**
 * BirdgramScreen - 100% Real Official Telegram Client.
 *
 * Connects directly to real Telegram servers:
 * - Real phone number login & real Telegram verification codes (OTP).
 * - Real user chat list, channels, groups, and contacts.
 * - Real photos, voice notes, stickers, attachments.
 * - Session permanently saved on the device.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirdgramScreen(
    repository: BirdgramRepository,
    onNavigateToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionInfo by repository.sessionInfo.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // File Chooser for Photo, Video, and Document uploads in Telegram chats
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            val uris = mutableListOf<Uri>()
            if (intent?.clipData != null) {
                val clipData = intent.clipData!!
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else if (intent?.data != null) {
                uris.add(intent.data!!)
            }
            filePathCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // Smart Back Handler: navigate back within Telegram chat history, else return to Home
    BackHandler {
        val web = webViewRef
        if (web != null && web.canGoBack()) {
            web.goBack()
        } else {
            onNavigateToHome()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFF0F172A),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (canGoBack) {
                                IconButton(
                                    onClick = { webViewRef?.goBack() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // Telegram Styled Birdgram Brand
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Birdgram",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                        border = BorderStroke(0.6.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = sessionInfo.selectedVariant.displayName,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "রিয়েল টেলিগ্রাম অ্যাকাউন্ট • আসল চ্যাট লিস্ট",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Reload button
                            IconButton(
                                onClick = {
                                    hasError = false
                                    webViewRef?.reload()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = Color(0xFF94A3B8)
                                )
                            }

                            // More options menu
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (sessionInfo.selectedVariant == TelegramClientVariant.WEB_K)
                                                    "Web A ভার্সনে পরিবর্তন করুন"
                                                else
                                                    "Web K ভার্সনে পরিবর্তন করুন",
                                                color = Color.White
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            val nextVariant = if (sessionInfo.selectedVariant == TelegramClientVariant.WEB_K)
                                                TelegramClientVariant.WEB_A
                                            else
                                                TelegramClientVariant.WEB_K
                                            repository.setClientVariant(nextVariant)
                                            webViewRef?.loadUrl(nextVariant.url)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.SwapHoriz, null, tint = Color(0xFF38BDF8))
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text("Telegram অ্যাপে খুলুন", color = Color.White)
                                        },
                                        onClick = {
                                            showMenu = false
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://"))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/"))
                                                context.startActivity(webIntent)
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.OpenInNew, null, tint = Color(0xFF34D399))
                                        }
                                    )

                                    HorizontalDivider(color = Color(0xFF334155))

                                    DropdownMenuItem(
                                        text = {
                                            Text("অ্যাকাউন্ট লগআউট / রিসেট", color = Color(0xFFF87171))
                                        },
                                        onClick = {
                                            showMenu = false
                                            showLogoutDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFF87171))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Linear Progress Bar while page loads
                    if (isLoading) {
                        LinearProgressIndicator(
                            progress = { loadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = Color(0xFF38BDF8),
                            trackColor = Color(0xFF1E293B)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF070D1B),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = 74.dp) // Leave clearance for floating bottom navigation dock
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)

                        // Enable Cookies & Session Persistence
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        // Comprehensive HTML5 Web Settings for modern Telegram Client
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(false)
                            // Modern Chrome Mobile UA to ensure Telegram renders full Web app without restrictions
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36"
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                hasError = false
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    hasError = true
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUrl = request?.url?.toString() ?: return false

                                // Handle Telegram native deep links & app intents safely
                                if (targetUrl.startsWith("tg://") || targetUrl.startsWith("intent://")) {
                                    try {
                                        val intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME)
                                        if (intent != null) {
                                            view?.context?.startActivity(intent)
                                            return true
                                        }
                                    } catch (_: Exception) {
                                        // Ignore unknown schemes without throwing ERR_UNKNOWN_URL_SCHEME
                                        return true
                                    }
                                }

                                // Handle telephone and mail links
                                if (targetUrl.startsWith("tel:") || targetUrl.startsWith("mailto:")) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                        view?.context?.startActivity(intent)
                                        return true
                                    } catch (_: Exception) {
                                        return true
                                    }
                                }

                                // If it's a Telegram web link, stay inside this WebView
                                if (targetUrl.contains("web.telegram.org") || targetUrl.contains("t.me")) {
                                    return false
                                }

                                // External web links: open in browser
                                return try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                    view?.context?.startActivity(intent)
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                if (newProgress >= 100) {
                                    isLoading = false
                                }
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallbackInternal: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = filePathCallbackInternal

                                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "*/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }
                                try {
                                    fileChooserLauncher.launch(intent)
                                } catch (_: Exception) {
                                    val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                    }
                                    fileChooserLauncher.launch(fallbackIntent)
                                }
                                return true
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                // Allow microphone (voice messages) and camera (QR scanner)
                                request?.grant(request.resources)
                            }
                        }

                        // Load user's selected official Telegram Web client
                        loadUrl(sessionInfo.selectedVariant.url)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                }
            )

            // Offline / Connection Error State
            AnimatedVisibility(
                visible = hasError,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "টেলিগ্রাম সার্ভারে সংযোগ করা যায়নি",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ইন্টারনেট সংযোগ চেক করুন অথবা অন্য ভার্সন (Web K / Web A) ট্রাই করুন।",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    hasError = false
                                    isLoading = true
                                    webViewRef?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("পুনরায় চেষ্টা করুন", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val nextVariant = if (sessionInfo.selectedVariant == TelegramClientVariant.WEB_K)
                                        TelegramClientVariant.WEB_A
                                    else
                                        TelegramClientVariant.WEB_K
                                    repository.setClientVariant(nextVariant)
                                    hasError = false
                                    isLoading = true
                                    webViewRef?.loadUrl(nextVariant.url)
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8))
                            ) {
                                Text("অন্য ভার্সন ট্রাই করুন", color = Color(0xFF38BDF8))
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for logging out / clearing saved session
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text("টেলিগ্রাম সেশন রিসেট করবেন?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "এটি আপনার ডিভাইসে সংরক্ষিত টেলিগ্রাম লগইন কুকি মুছে ফেলবে এবং আপনাকে পুনরায় ফোন নম্বর দিয়ে লগইন করতে হবে।",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        repository.clearTelegramSession {
                            coroutineScope.launch {
                                webViewRef?.clearCache(true)
                                webViewRef?.loadUrl(sessionInfo.selectedVariant.url)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("রিসেট ও লগআউট", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
