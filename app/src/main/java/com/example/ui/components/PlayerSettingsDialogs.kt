package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.player.VideoTrackQuality
import java.util.Locale

@Composable
fun PlaybackSpeedDialog(
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val formattedSpeed = String.format(Locale.US, "%.2f", playbackSpeed)
    val presets = listOf(0.5f, 0.75f, 1.0f, 1.05f, 1.10f, 1.15f, 1.20f, 1.25f, 1.35f, 1.5f, 1.75f, 2.0f, 2.5f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("প্লেব্যাক স্পিড (Speed Control)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${formattedSpeed}x",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val next = (playbackSpeed - 0.10f).coerceAtLeast(0.25f)
                            onSpeedChange(Math.round(next * 100) / 100f)
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-0.10", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val next = (playbackSpeed - 0.05f).coerceAtLeast(0.25f)
                            onSpeedChange(Math.round(next * 100) / 100f)
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-0.05", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val next = (playbackSpeed + 0.05f).coerceAtMost(3.00f)
                            onSpeedChange(Math.round(next * 100) / 100f)
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+0.05", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val next = (playbackSpeed + 0.10f).coerceAtMost(3.00f)
                            onSpeedChange(Math.round(next * 100) / 100f)
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+0.10", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Slider(
                    value = playbackSpeed,
                    onValueChange = { raw ->
                        val stepped = Math.round(raw / 0.05f) * 0.05f
                        val finalSpeed = (Math.round(stepped * 100) / 100f).coerceIn(0.25f, 3.00f)
                        onSpeedChange(finalSpeed)
                    },
                    valueRange = 0.25f..3.00f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "দ্রুত নির্বাচন (Presets):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { p ->
                        val isSel = Math.abs(playbackSpeed - p) < 0.02f
                        FilterChip(
                            selected = isSel,
                            onClick = { onSpeedChange(p) },
                            label = { Text("${p}x") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("সম্পূর্ণ")
            }
        }
    )
}

@Composable
fun VideoQualityDialog(
    availableQualities: List<VideoTrackQuality>,
    selectedQualityLabel: String,
    onSelectQuality: (VideoTrackQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("ভিডিও রেজোলিউশন / কোয়ালিটি", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (availableQualities.isEmpty()) {
                    Text(
                        "অটো রেজোলিউশন চলছে (বা সিলেক্টেড স্ট্রিমে একটাই কোয়ালিটি লভ্য)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    availableQualities.forEach { quality ->
                        val isSelected = selectedQualityLabel == quality.label
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectQuality(quality)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = quality.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        }
    )
}
