package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.ui.components.SlideViewerDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val allDownloads by downloadManager.getAllDownloads().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Video, 1: PDF
    var itemToDelete by remember { mutableStateOf<DownloadedItemEntity?>(null) }
    var activePdfViewerItem by remember { mutableStateOf<DownloadedItemEntity?>(null) }

    val videoDownloads = remember(allDownloads) {
        allDownloads.filter { it.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true) }
    }
    val pdfDownloads = remember(allDownloads) {
        allDownloads.filter { it.fileType.equals(DownloadedItemEntity.FILE_TYPE_PDF, ignoreCase = true) }
    }

    val currentList = if (selectedTab == 0) videoDownloads else pdfDownloads

    // In-App PDF Viewer for downloaded PDFs
    if (activePdfViewerItem != null) {
        val pdfItem = activePdfViewerItem!!
        SlideViewerDialog(
            slideUrl = pdfItem.localFilePath,
            title = pdfItem.title,
            onDismiss = { activePdfViewerItem = null }
        )
    }

    // Deletion Confirmation Dialog
    if (itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = "ডাউনলোড মুছে ফেলবেন?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "আপনি কি নিশ্চিত যে \"${item.title}\" ফাইলটি আপনার অফলাইন স্টোরেজ থেকে ডিলিট করতে চান?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadManager.deleteDownloadedFile(item.id)
                        itemToDelete = null
                        Toast.makeText(context, "ফাইলটি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("মুছে ফেলুন", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .testTag("downloads_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "অফলাইন ডাউনলোড",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "অ্যাপের ভেতর সুরক্ষিতভাবে সংরক্ষিত",
                                fontSize = 12.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Total Downloads Count Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "${allDownloads.size} টি ফাইল",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Tab Switcher (ভিডিও লেকচার | ই-বুক ও নোট)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Tab 0: ভিডিও
                                val isTab0 = selectedTab == 0
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = if (isTab0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shadowElevation = if (isTab0) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(26.dp))
                                        .clickable { selectedTab = 0 }
                                        .testTag("tab_download_videos")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "ভিডিও লেকচার (${videoDownloads.size})",
                                            fontSize = 13.sp,
                                            fontWeight = if (isTab0) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isTab0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Tab 1: পিডিএফ
                                val isTab1 = selectedTab == 1
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = if (isTab1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shadowElevation = if (isTab1) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(26.dp))
                                        .clickable { selectedTab = 1 }
                                        .testTag("tab_download_pdfs")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "ই-বুক ও নোট (${pdfDownloads.size})",
                                            fontSize = 13.sp,
                                            fontWeight = if (isTab1) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isTab1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("downloads_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentList.isEmpty()) {
                EmptyDownloadsView(
                    isVideosTab = selectedTab == 0,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentList, key = { it.id }) { item ->
                        DownloadedItemCard(
                            item = item,
                            downloadManager = downloadManager,
                            onOpen = {
                                if (item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)) {
                                    onPlayVideo(
                                        item.localFilePath,
                                        item.title,
                                        item.subtitle ?: "অফলাইন ভিডিও",
                                        "#0072EC",
                                        false
                                    )
                                } else {
                                    activePdfViewerItem = item
                                }
                            },
                            onCancel = {
                                downloadManager.cancelDownload(item.id)
                                Toast.makeText(context, "ডাউনলোড বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedItemCard(
    item: DownloadedItemEntity,
    downloadManager: AppFileDownloadManager,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)
    val isCompleted = item.status == DownloadedItemEntity.STATUS_COMPLETED
    val isDownloading = item.status == DownloadedItemEntity.STATUS_DOWNLOADING

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = isCompleted, onClick = onOpen)
            .testTag("download_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type Icon Box
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isVideo) Color(0xFF0072EC).copy(alpha = 0.12f)
                            else Color(0xFFE53935).copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.PlayCircleFilled else Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = if (isVideo) Color(0xFF0072EC) else Color(0xFFE53935),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title.ifBlank { if (isVideo) "ভিডিও লেকচার" else "নোট" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!item.subtitle.isNullOrBlank()) {
                            Text(
                                text = item.subtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (isCompleted) {
                            Text(
                                text = downloadManager.formatFileSize(item.totalBytes),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981)
                            )
                        } else if (isDownloading) {
                            Text(
                                text = "${downloadManager.formatFileSize(item.downloadedBytes)} / ${downloadManager.formatFileSize(item.totalBytes)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "ব্যর্থ হয়েছে",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action Buttons
                if (isDownloading) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Cancel Download",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Progress Bar if Downloading
            if (isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { item.progressFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${item.progressPercent}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDownloadsView(
    isVideosTab: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideosTab) Icons.Default.CloudDownload else Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isVideosTab) "কোনো ডাউনলোডকৃত ভিডিও নেই" else "কোনো ডাউনলোডকৃত নোট নেই",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "ক্লাস বা স্মার্ট নোট দেখার সময় অফলাইন ডাউনলোড বাটনে চাপ দিয়ে ইন্টারনেট ছাড়াই দেখার জন্য সংরক্ষণ করুন।",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}
