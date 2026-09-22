package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
    var hasError by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var localPdfFile by remember { mutableStateOf<File?>(null) }

    val isLocalFile = remember(localPdfFile) {
        localPdfFile != null
    }

    // Proactive reactive download to cache (if not already downloaded in vaulted storage)
    LaunchedEffect(slideUrl, downloadedItem) {
        if (downloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED) {
            val file = File(downloadedItem!!.localFilePath)
            if (file.exists() && file.length() > 0) {
                localPdfFile = file
                isLoading = false
                hasError = false
                return@LaunchedEffect
            }
        }

        // Check if there is already a completely cached version of this file
        val cacheFile = File(context.cacheDir, "temp_pdf_${slideUrl.hashCode().toString().replace("-", "n")}.pdf")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            localPdfFile = cacheFile
            isLoading = false
            hasError = false
            return@LaunchedEffect
        }

        // Start caching download
        isLoading = true
        hasError = false
        downloadProgress = 0f

        withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(slideUrl)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                val body = response.body ?: throw IOException("Empty body")
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                
                // Save to a temporary suffix file, then rename to atomic safe complete
                val tempFile = File(context.cacheDir, "temp_pdf_${slideUrl.hashCode().toString().replace("-", "n")}.pdf.tmp")
                val outputStream = FileOutputStream(tempFile)
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var totalRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        downloadProgress = totalRead.toFloat() / totalBytes
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (tempFile.renameTo(cacheFile) || cacheFile.exists()) {
                    localPdfFile = cacheFile
                } else {
                    localPdfFile = tempFile
                }
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                hasError = true
                isLoading = false
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
                .background(Color(0xFFF1F5F9))
        ) {
            // Header Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
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
                                text = if (downloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED) "অফলাইন সংরক্ষিত লেকচার স্লাইড" else "পিডিএফ লেকচার স্লাইড ভিউয়ার",
                                fontSize = 11.sp,
                                color = if (downloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (downloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED) FontWeight.SemiBold else FontWeight.Normal
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

            // PDF Render Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (localPdfFile != null) {
                    NativePdfViewer(file = localPdfFile!!)
                } else if (isLoading) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "স্লাইড লোড হচ্ছে...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ডাউনলোড হচ্ছে: ${(downloadProgress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (hasError) {
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

@Composable
fun NativePdfViewer(file: File, modifier: Modifier = Modifier) {
    val fileDescriptor = remember(file) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val pdfRenderer = remember(fileDescriptor) {
        if (fileDescriptor != null) {
            try {
                PdfRenderer(fileDescriptor)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null
    }

    DisposableEffect(pdfRenderer, fileDescriptor) {
        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (pdfRenderer == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "পিডিএফ স্লাইড ভিউয়ার শুরু করা যাচ্ছে না।",
                fontSize = 14.sp,
                color = Color.Red,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        val pageCount = pdfRenderer.pageCount
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFE2E8F0)),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pageCount) { index ->
                NativePdfPageItem(pdfRenderer = pdfRenderer, pageIndex = index)
            }
        }
    }
}

@Composable
fun NativePdfPageItem(pdfRenderer: PdfRenderer, pageIndex: Int, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val page = pdfRenderer.openPage(pageIndex)
                // Use a high-quality resolution (1200px wide for sharp texts)
                val width = 1200
                val height = (width.toFloat() / page.width * page.height).toInt()
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 12.dp)
            .background(Color.White, RoundedCornerShape(6.dp))
            .border(0.5.dp, Color.LightGray, RoundedCornerShape(6.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "স্লাইড পৃষ্ঠা ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
