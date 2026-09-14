package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem

/**
 * 100ms Prebuilt Live Class "Get Started" Screen
 */
@Composable
fun LiveGetStartedScreen(
    lesson: StudentLessonItem?,
    effectiveMeetingUrl: String,
    onJoinClick: () -> Unit,
    onBack: () -> Unit
) {
    var userName by remember { mutableStateOf("Fahim Miya_7191") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B11))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top App Bar with circular back button and report icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF191B23),
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF191B23),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = "Report/Feedback",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Center Content: "Get Started", subtitle, and "350 others in session" badge
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Enter your name before joining",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF9EABB8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Session participants badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF191B23),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "350 others in session",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            // Bottom Section: Wifi network indicator + Name Input Box + "Join Now" Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // Wifi indicator chip on left
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF191B23),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Network Status",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Name text field box
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF11131B),
                            unfocusedContainerColor = Color(0xFF11131B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF2572ED),
                            unfocusedBorderColor = Color(0xFF272932)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )

                    // "Join Now" Button
                    Button(
                        onClick = onJoinClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2572ED)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    ) {
                        Text(
                            text = "Join Now",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
