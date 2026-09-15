package com.example.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.SessionManager
import com.example.utils.QrSessionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrSessionLoginSheet(
    sessionManager: SessionManager,
    onLoginSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Paste Code, 1: Gallery QR, 2: Camera Scanner
    var tokenInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            val qrText = QrSessionUtils.decodeQrFromUri(context, uri)
            if (qrText != null) {
                val success = QrSessionUtils.importSessionPayload(qrText, sessionManager)
                isLoading = false
                if (success) {
                    Toast.makeText(context, "কিউআর সেশন সফলভাবে ইনপোর্ট হয়েছে!", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                } else {
                    errorMessage = "কিউআর কোডে কোনো বৈধ সেশন টোকেন পাওয়া যায়নি।"
                }
            } else {
                isLoading = false
                errorMessage = "ছবিটি থেকে কিউআর কোড স্ক্যান করা সম্ভব হয়নি। সঠিক সেশন কিউআর কোডের ছবি নির্বাচন করুন।"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "কিউআর বা সেশন লগইন",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    listOf("পেস্ট কোড", "গ্যালারি ছবি", "ক্যামেরা").forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                                .clickable {
                                    selectedTab = index
                                    errorMessage = null
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                when (selectedTab) {
                    0 -> { // Paste Code
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = {
                                tokenInput = it
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("এখানে সেশন কোড বা টোকেন পেস্ট করুন...", fontSize = 13.sp) },
                            shape = RoundedCornerShape(14.dp),
                            minLines = 3,
                            maxLines = 4,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        val pasteText = clipData.getItemAt(0).text?.toString() ?: ""
                                        if (pasteText.isNotBlank()) {
                                            tokenInput = pasteText
                                            errorMessage = null
                                            Toast.makeText(context, "ক্লিপবোর্ড থেকে পেস্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (tokenInput.isBlank()) {
                                    errorMessage = "অনুগ্রহ করে সেশন কোডটি ইনপুট দিন।"
                                    return@Button
                                }
                                val success = QrSessionUtils.importSessionPayload(tokenInput, sessionManager)
                                if (success) {
                                    Toast.makeText(context, "সেশন ইনপোর্ট সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "অবৈধ সেশন কোড। সঠিকভাবে সেশন কোড দিন।"
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("সেশন কোড দিয়ে লগইন করুন", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    1 -> { // Gallery Image QR Picker
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "অন্য ডিভাইস থেকে পাওয়া সেশন কিউআর কোডের স্ক্রিনশট বা ছবিটি আপনার গ্যালারি থেকে সিলেক্ট করুন।",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedButton(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("গ্যালারি থেকে কিউআর ছবি সিলেক্ট করুন", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    2 -> { // Camera QR Scanner
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.85f))
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "কিউআর কোডের দিকে ফ্রেমটি রাখুন",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("গ্যালারি থেকে কিউআর ছবি দিন", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }

                // Error Banner
                AnimatedVisibility(visible = errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
