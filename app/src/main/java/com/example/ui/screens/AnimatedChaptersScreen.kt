package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.AcademicChapterItem
import com.example.course.AnimatedLessonsViewModel
import com.example.utils.toBengaliDigits

@Composable
fun AnimatedChaptersScreen(
    subjectCode: String,
    subjectTitle: String,
    viewModel: AnimatedLessonsViewModel,
    onBack: () -> Unit,
    onChapterClick: (chapterId: String, chapterName: String, subjectCode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.chaptersUiState.collectAsState()

    LaunchedEffect(subjectCode) {
        if (subjectCode.isNotBlank() && (uiState.subjectCode != subjectCode || uiState.chapters.isEmpty())) {
            viewModel.loadAnimatedChapters(subjectCode, subjectTitle)
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFEBF3FF),
                                Color(0xFFF8FAFC),
                                Color.White
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("animated_chapters_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (subjectTitle.isNotBlank()) subjectTitle else uiState.subjectTitle,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val countBn = toBengaliDigits(uiState.chapters.size)
                        Text(
                            text = "সর্বমোট $countBn টি অধ্যায়",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF64748B)
                        )
                    }

                    AnimatedLessonBrandLogoHeader()
                }
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.testTag("animated_chapters_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3B82F6),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "এনিমেটেড অধ্যায়সমূহ লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                uiState.chapters.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "কোনো এনিমেটেড অধ্যায় পাওয়া যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadAnimatedChapters(subjectCode, subjectTitle) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পুনরায় চেষ্টা করুন")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = uiState.chapters,
                            key = { index, item -> item.id.ifBlank { "chap_$index" } }
                        ) { index, chapter ->
                            AnimatedChapterItemRow(
                                chapter = chapter,
                                index = index,
                                onClick = {
                                    onChapterClick(
                                        chapter.id,
                                        chapter.effectiveName,
                                        subjectCode
                                    )
                                }
                            )
                            if (index < uiState.chapters.size - 1) {
                                HorizontalDivider(
                                    color = Color(0xFFE2E8F0).copy(alpha = 0.6f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedChapterItemRow(
    chapter: AcademicChapterItem,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.effectiveName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                val chNoStr = chapter.effectiveNo?.toString() ?: index.toString()
                Text(
                    text = "অধ্যায় ${toBengaliDigits(chNoStr)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = CircleShape,
                color = Color(0xFFEBF3FF),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Animated Lessons",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedLessonBrandLogoHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(end = 4.dp),
        horizontalAlignment = Alignment.End
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("অ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF59E0B))
            Text("আ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF06B6D4))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ক", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEC4899))
            Text("খ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3B82F6))
        }
    }
}
