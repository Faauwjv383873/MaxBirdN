package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TerminalLog(
    val id: Long = System.currentTimeMillis() + (0..1000).random(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val tag: String,
    val message: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INFO, SUCCESS, ERROR, WARNING
}

object DebugTerminalManager {
    private val _logs = MutableStateFlow<List<TerminalLog>>(emptyList())
    val logs = _logs.asStateFlow()

    fun log(tag: String, message: String, type: LogType = LogType.INFO) {
        val newLog = TerminalLog(tag = tag, message = message, type = type)
        val updated = (_logs.value + newLog).takeLast(200)
        _logs.value = updated
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

@Composable
fun DebugTerminalOverlay(modifier: Modifier = Modifier) {
    val logs by DebugTerminalManager.logs.collectAsState()
    var isOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && isOpen) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        if (!isOpen) {
            FloatingActionButton(
                onClick = { isOpen = true },
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color(0xFF00FF66),
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("terminal_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("টার্মিনাল (${logs.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .padding(8.dp)
                    .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(16.dp))
                            Text("লাইভ ডিবাগ টার্মিনাল (Shikho API & Player)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { DebugTerminalManager.clear() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { isOpen = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0A0A0A))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (logs.isEmpty()) {
                            item {
                                Text(
                                    "কোনো লগ নেই। ক্লাসে বা কোর্সে ক্লিক করুন...",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        items(logs, key = { it.id }) { log ->
                            val color = when (log.type) {
                                LogType.SUCCESS -> Color(0xFF00FF66)
                                LogType.ERROR -> Color(0xFFFF5555)
                                LogType.WARNING -> Color(0xFFFFCC00)
                                LogType.INFO -> Color(0xFF00DDFF)
                            }
                            Text(
                                text = "[${log.timestamp}] [${log.tag}] ${log.message}",
                                color = color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
