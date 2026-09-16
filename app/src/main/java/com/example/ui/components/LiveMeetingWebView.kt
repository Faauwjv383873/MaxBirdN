package com.example.ui.components

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LiveMeetingWebView(
    meetingUrl: String,
    studentName: String = "Student",
    authToken: String? = null,
    onStreamDiscovered: (String) -> Unit,
    onBackToStream: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isLinkFound by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .then(if (isLinkFound) Modifier.size(1.dp) else Modifier.fillMaxSize())
            .background(if (isLinkFound) Color.Transparent else Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Shikho সেশন বজায় রাখতে কুকি সিঙ্ক করা
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    if (!authToken.isNullOrBlank()) {
                        cookieManager.setCookie("https://app.shikho.com", "token=$authToken; Path=/; Secure;")
                        cookieManager.setCookie("https://app.shikho.com", "auth_token=$authToken; Path=/; Secure;")
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Shikho/6.0.7"
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: ""

                            // 100ms লাইভ স্ট্রিম ও master.m3u8 ক্যাপচার
                            if ((reqUrl.contains("100ms.live") || reqUrl.contains("sh-cdn")) && 
                                (reqUrl.contains("master.m3u8") || reqUrl.contains(".m3u8"))
                            ) {
                                if (!isLinkFound) {
                                    isLinkFound = true
                                    view?.post {
                                        onStreamDiscovered(reqUrl)
                                    }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)

                            // Shikho Web ও 100ms পেজে অটো-জয়েন স্ক্রিপ্ট
                            val autoJoinScript = """
                                (function() {
                                    // LocalStorage এ টোকেন ইনজেক্ট করা
                                    try {
                                        if ('$authToken' !== '') {
                                            localStorage.setItem('auth_token', '$authToken');
                                            localStorage.setItem('token', '$authToken');
                                        }
                                    } catch(e) {}

                                    var count = 0;
                                    var timer = setInterval(function() {
                                        count++;

                                        // ইনপুট ফিল্ডে নাম বসানো
                                        var inputs = document.querySelectorAll('input[type="text"], input[name="name"]');
                                        inputs.forEach(function(inp) {
                                            if (!inp.value || inp.value !== '$studentName') {
                                                var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                                                if (nativeSetter) {
                                                    nativeSetter.call(inp, '$studentName');
                                                } else {
                                                    inp.value = '$studentName';
                                                }
                                                inp.dispatchEvent(new Event('input', { bubbles: true }));
                                                inp.dispatchEvent(new Event('change', { bubbles: true }));
                                            }
                                        });

                                        // 'Join Now' / 'যুক্ত হোন' / 'Get Started' বাটনে ক্লিক
                                        var buttons = document.querySelectorAll('button');
                                        for (var i = 0; i < buttons.length; i++) {
                                            var btn = buttons[i];
                                            var txt = (btn.innerText || btn.textContent || '').toLowerCase();
                                            if ((txt.includes('join') || txt.includes('started') || txt.includes('যুক্ত') || txt.includes('ক্লাসে প্রবেশ')) && !btn.disabled) {
                                                btn.click();
                                                clearInterval(timer);
                                                break;
                                            }
                                        }
                                        if (count > 25) clearInterval(timer);
                                    }, 600);
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(autoJoinScript, null)
                        }
                    }

                    // হেডার সহ আসল পেজ লোড
                    val headers = mutableMapOf<String, String>()
                    if (!authToken.isNullOrBlank()) {
                        headers["Authorization"] = "Bearer $authToken"
                    }
                    headers["Referer"] = "https://app.shikho.com/"
                    loadUrl(meetingUrl, headers)
                }
            },
            modifier = if (isLinkFound) Modifier.size(1.dp) else Modifier.fillMaxSize()
        )

        if (!isLinkFound) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFEF4444), modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "লাইভ স্ট্রিম সংযোগ করা হচ্ছে...",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
