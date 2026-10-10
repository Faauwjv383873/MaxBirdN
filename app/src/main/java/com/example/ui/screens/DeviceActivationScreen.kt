package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.ActivationResult
import com.example.security.ActivationSupportLink
import com.example.security.ActivationSupportRepository
import com.example.security.DeviceActivationRepository
import com.example.security.DeviceSecurityManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActivationScreen(
    onActivationSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var accessCodeInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isCopied by remember { mutableStateOf(false) }

    val displayDeviceId = remember { DeviceSecurityManager.getDisplayDeviceId(context) }
    val isSupabaseConfigured = remember { DeviceActivationRepository.isConfigured() }

    // Dynamic support links fetched from Supabase with instant offline-first cache
    var supportLinks by remember {
        mutableStateOf<List<ActivationSupportLink>>(ActivationSupportRepository.getCachedLinks(context))
    }

    LaunchedEffect(Unit) {
        try {
            val freshLinks = ActivationSupportRepository.fetchSupportLinks(context)
            if (freshLinks.isNotEmpty()) {
                supportLinks = freshLinks
            }
        } catch (t: Throwable) {
            Log.e("DeviceActivation", "Error loading fresh support links: ${t.message}")
        }
    }

    // High security pulsing shield animation
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    fun handleCopyId() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Device ID", displayDeviceId)
        clipboard.setPrimaryClip(clip)
        isCopied = true
        Toast.makeText(context, "ডিভাইস আইডি কপি হয়েছে!", Toast.LENGTH_SHORT).show()
        coroutineScope.launch {
            delay(2000)
            isCopied = false
        }
    }

    fun handleShareId() {
        try {
            val shareText = "আমার MaxBird অ্যাপের ডিভাইস আইডি:\n$displayDeviceId\n\nঅনুগ্রহ করে আমাকে এক্সেস কোড প্রদান করুন।"
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "ডিভাইস আইডি শেয়ার করুন"))
        } catch (_: Exception) {
            handleCopyId()
        }
    }

    fun handlePasteCode() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (!item.isNullOrBlank()) {
                accessCodeInput = item.trim().uppercase()
                errorMessage = null
                Toast.makeText(context, "কোড পেস্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "ক্লিপবোর্ডে কোনো কোড নেই", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "পেস্ট করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleOpenLink(link: ActivationSupportLink) {
        try {
            var targetUrl = link.url.trim()
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://") && !targetUrl.startsWith("tel:") && !targetUrl.startsWith("mailto:")) {
                targetUrl = "https://$targetUrl"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DeviceActivation", "Cannot open link: ${link.url}", e)
            handleCopyId()
            Toast.makeText(context, "লিংক ওপেন করা যায়নি। ডিভাইস আইডি কপি করা হয়েছে।", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleActivate() {
        val code = accessCodeInput.trim()
        if (code.isBlank()) {
            errorMessage = "দয়া করে এক্সেস কোডটি লিখুন।"
            return
        }

        try {
            keyboardController?.hide()
        } catch (_: Throwable) {}

        isLoading = true
        errorMessage = null
        successMessage = null

        coroutineScope.launch {
            try {
                if (!isSupabaseConfigured) {
                    isLoading = false
                    errorMessage = "Supabase কনফিগারেশন এখনো সংযুক্ত করা হয়নি। অনুগ্রহ করে GitHub Secrets এ SUPABASE_URL এবং SUPABASE_ANON_KEY যোগ করে নতুন বিল্ড করুন।"
                    return@launch
                }

                val result = DeviceActivationRepository.activateDevice(context, code)
                isLoading = false
                when (result) {
                    is ActivationResult.Success -> {
                        successMessage = result.message
                        delay(900)
                        try {
                            onActivationSuccess()
                        } catch (t: Throwable) {
                            Log.e("DeviceActivation", "Navigation error: ${t.message}", t)
                        }
                    }
                    is ActivationResult.Error -> {
                        errorMessage = result.message
                    }
                }
            } catch (t: Throwable) {
                Log.e("DeviceActivation", "Error in handleActivate: ${t.message}", t)
                isLoading = false
                errorMessage = "ত্রুটি ঘটেছে: ${t.localizedMessage ?: "দয়া করে আবার চেষ্টা করুন"}"
            }
        }
    }

    // High security luxury dark cyber gradient background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF060D1A),
            Color(0xFF0C192E),
            Color(0xFF081224)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Cyber Security Shield Emblem with glowing multi-layer ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(104.dp)
                    .scale(pulseScale)
            ) {
                // Outer glowing circle
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6).copy(alpha = 0.35f),
                                    Color(0xFF1D4ED8).copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Inner shield container with glassmorphic border
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF16233B),
                    border = BorderStroke(2.dp, Color(0xFF60A5FA).copy(alpha = 0.7f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Security Shield",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Badge Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.8f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "সুরক্ষিত ডিভাইস প্রোটেকশন",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            // 2. Title & Subtitle
            Text(
                text = "ডিভাইস সুরক্ষা ও এক্সেস কন্ট্রোল",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "এই অ্যাপটি সম্পূর্ণ প্রাইভেট ও নিরাপদ। অনুমোদিত ডিভাইসে এক্সেস দেওয়ার জন্য অ্যাডমিনের দেওয়া সিক্রেট কোড প্রয়োজন।",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 3. Unique Hardware Device ID Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1A2D),
                border = BorderStroke(1.dp, Color(0xFF1E2E4A)),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "আপনার অনন্য ডিভাইস আইডি",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Monospace ID display box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF070F1E),
                        border = BorderStroke(1.dp, Color(0xFF253755)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { handleCopyId() }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = displayDeviceId,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.weight(1f)
                            )

                            // Animated copy status
                            IconButton(
                                onClick = { handleCopyId() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("copy_device_id_button")
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                                    contentDescription = "Copy Device ID",
                                    tint = if (isCopied) Color(0xFF10B981) else Color(0xFF60A5FA),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons row: Copy and Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { handleCopyId() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isCopied) Color(0xFF065F46) else Color(0xFF1E2E4A),
                                contentColor = if (isCopied) Color(0xFFA7F3D0) else Color(0xFF93C5FD)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCopied) "কপি হয়েছে!" else "আইডি কপি",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FilledTonalButton(
                            onClick = { handleShareId() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1E2E4A),
                                contentColor = Color(0xFF93C5FD)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "শেয়ার করুন",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "কোড সংগ্রহ করতে উপরের আইডিটি কপি করে অ্যাডমিনকে পাঠান",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Access Code Input Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1A2D),
                border = BorderStroke(1.dp, Color(0xFF1E2E4A)),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "এক্সেস কোড লিখুন",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )

                        // Quick Paste button if clipboard has content
                        TextButton(
                            onClick = { handlePasteCode() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "পেস্ট করুন",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF60A5FA)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = accessCodeInput,
                        onValueChange = {
                            accessCodeInput = it.uppercase()
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activation_code_input"),
                        placeholder = {
                            Text(
                                "যেমন: VIP-STUDENT-01",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (accessCodeInput.isNotBlank()) {
                                IconButton(onClick = { accessCodeInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF25334D),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF3B82F6),
                            focusedContainerColor = Color(0xFF070F1E),
                            unfocusedContainerColor = Color(0xFF070F1E)
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { handleActivate() }
                        )
                    )

                    // Error Message Banner
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF451A1A),
                            border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errorMessage.orEmpty(),
                                    fontSize = 12.sp,
                                    color = Color(0xFFFCA5A5),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    // Success Message Banner
                    AnimatedVisibility(
                        visible = successMessage != null,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF064E3B),
                            border = BorderStroke(1.dp, Color(0xFF059669)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = successMessage.orEmpty(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFA7F3D0)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Activate Button
                    Button(
                        onClick = { handleActivate() },
                        enabled = !isLoading && successMessage == null,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            disabledContainerColor = Color(0xFF1E3A8A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("activate_device_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "যাচাই করা হচ্ছে...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "এক্সেস সক্রিয় করুন",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 5. Contact Admin & Support Links Section (Completely redesigned, no squishing, dynamic support links)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0D1728),
                border = BorderStroke(1.dp, Color(0xFF1E2E4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1E2E4A),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "কোড পেতে অ্যাডমিনের সাথে যোগাযোগ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "আপনার ডিভাইস আইডি পাঠিয়ে যেকোনো মাধ্যমে কোড নিন",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // List of dynamic contact links configured from Admin app
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        supportLinks.forEach { link ->
                            SupportLinkCard(
                                link = link,
                                onClick = { handleOpenLink(link) }
                            )
                        }
                    }
                }
            }

            // Extra space at bottom to prevent navigation bar clipping
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Modern, un-squished support link card with custom brand accents.
 */
@Composable
private fun SupportLinkCard(
    link: ActivationSupportLink,
    onClick: () -> Unit
) {
    val brandColor = remember(link.iconType, link.colorHex) {
        if (!link.colorHex.isNullOrBlank()) {
            try {
                Color(android.graphics.Color.parseColor(link.colorHex))
            } catch (_: Exception) {
                getBrandColorForType(link.iconType)
            }
        } else {
            getBrandColorForType(link.iconType)
        }
    }

    val iconVector: ImageVector = remember(link.iconType) {
        getBrandIconForType(link.iconType)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF081222),
        border = BorderStroke(1.dp, brandColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_link_${link.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Brand Icon Badge
                Surface(
                    shape = CircleShape,
                    color = brandColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, brandColor.copy(alpha = 0.5f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = link.title,
                            tint = brandColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = link.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!link.subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = link.subtitle,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action arrow icon
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E2E4A).copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open",
                        tint = brandColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun getBrandColorForType(type: String): Color {
    return when (type.lowercase().trim()) {
        "telegram" -> Color(0xFF229ED9)
        "whatsapp" -> Color(0xFF25D366)
        "facebook" -> Color(0xFF1877F2)
        "phone", "call" -> Color(0xFFF59E0B)
        "website", "web" -> Color(0xFF38BDF8)
        else -> Color(0xFF60A5FA)
    }
}

private fun getBrandIconForType(type: String): ImageVector {
    return when (type.lowercase().trim()) {
        "telegram" -> Icons.AutoMirrored.Filled.Send
        "whatsapp" -> Icons.Filled.OpenInNew
        "phone", "call" -> Icons.Filled.Phone
        "website", "web" -> Icons.Filled.Language
        else -> Icons.Filled.OpenInNew
    }
}
