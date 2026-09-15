package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import java.io.File
import java.net.URLEncoder

@Composable
fun SlideViewerDialog(
    slideUrl: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val downloadId = remember(slideUrl, title) {
        "pdf_" + (slideUrl.hashCode().toString() + "_" + title.hashCode().toString()).replace("-", "n")
    }
    val downloadedItem by downloadManager.getDownloadedItemById(downloadId).collectAsState(initial = null)

    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var hasError by remember { mutableStateOf(false) }

    val effectiveUrl = remember(slideUrl, downloadedItem) {
        if (downloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED) {
            val file = File(downloadedItem!!.localFilePath)
            if (file.exists() && file.length() > 0) file.absolutePath else slideUrl
        } else {
            slideUrl
        }
    }

    val isLocalFile = remember(effectiveUrl) {
        effectiveUrl.startsWith("/") || effectiveUrl.startsWith("file://")
    }

    val viewerUrl = remember(effectiveUrl, isLocalFile) {
        if (isLocalFile) {
            if (effectiveUrl.startsWith("file://")) effectiveUrl else "file://$effectiveUrl"
        } else {
            try {
                val encoded = URLEncoder.encode(effectiveUrl, "UTF-8")
                "https://docs.google.com/gview?embedded=true&url=$encoded"
            } catch (_: Exception) {
                effectiveUrl
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            // Header Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "বন্ধ করুন"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isLocalFile) "অফলাইন সংরক্ষিত লেকচার স্লাইড" else "পিডিএফ লেকচার স্লাইড ভিউয়ার",
                                fontSize = 11.sp,
                                color = if (isLocalFile) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isLocalFile) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // In-App Offline Download Action
                        when (downloadedItem?.status) {
                            DownloadedItemEntity.STATUS_DOWNLOADING -> {
                                val item = downloadedItem!!
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { item.progressFraction },
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            DownloadedItemEntity.STATUS_COMPLETED -> {
                                IconButton(
                                    onClick = {
                                        Toast.makeText(context, "এই ফাইলটি অফলাইনে সংরক্ষিত রয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DownloadDone,
                                        contentDescription = "অফলাইন ডাউনলোড সম্পন্ন",
                                        tint = Color(0xFF10B981)
                                    )
                                }
                            }
                            else -> {
                                IconButton(
                                    onClick = {
                                        downloadManager.downloadFile(
                                            id = downloadId,
                                            title = title,
                                            subtitle = "পিডিএফ লেকচার নোট",
                                            fileType = DownloadedItemEntity.FILE_TYPE_PDF,
                                            remoteUrl = slideUrl
                                        )
                                        Toast.makeText(context, "ইন-অ্যাপ অফলাইন ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "ইন-অ্যাপ ডাউনলোড করুন",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                openSlideInExternalApp(context, slideUrl)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "ব্রাউজারে ওপেন করুন"
                            )
                        }
                        IconButton(
                            onClick = {
                                copySlideToClipboard(context, slideUrl)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "লিংক কপি করুন"
                            )
                        }
                    }
                }
            }

            // Webview Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportZoom(true)
                                allowFileAccess = true
                                allowContentAccess = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    hasError = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    super.onReceivedError(view, errorCode, description, failingUrl)
                                    isLoading = false
                                    hasError = true
                                }
                            }
                            loadUrl(viewerUrl)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "স্লাইড লোড হচ্ছে...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (hasError) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "স্লাইড প্রিভিউ লোড হতে সমস্যা হয়েছে",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "সরাসরি ব্রাউজারে দেখতে বা ইন-অ্যাপ ডাউনলোড করতে নিচের বাটনে চাপুন।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        openSlideInExternalApp(context, slideUrl)
                                    }
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ব্রাউজারে খুলুন")
                                }
                                OutlinedButton(
                                    onClick = {
                                        downloadManager.downloadFile(
                                            id = downloadId,
                                            title = title,
                                            subtitle = "পিডিএফ লেকচার নোট",
                                            fileType = DownloadedItemEntity.FILE_TYPE_PDF,
                                            remoteUrl = slideUrl
                                        )
                                        Toast.makeText(context, "ইন-অ্যাপ অফলাইন ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ডাউনলোড")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openSlideInExternalApp(context: Context, url: String) {
    try {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "স্লাইড ওপেন করুন"))
    } catch (_: Exception) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "ব্রাউজার বা পিডিএফ রিডার পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun copySlideToClipboard(context: Context, url: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Lecture Slide URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "স্লাইড লিংক কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

