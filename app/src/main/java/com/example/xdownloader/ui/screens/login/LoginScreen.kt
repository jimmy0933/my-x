package com.example.xdownloader.ui.screens.login

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import android.view.ViewGroup
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.xdownloader.api.ApiResult
import com.example.xdownloader.api.AuthManager
import com.example.xdownloader.api.XApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val authManager = remember { AuthManager.getInstance(context) }
    val apiClient = remember { XApiClient(context) }
    val scope = rememberCoroutineScope()

    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    // Store these so we can retry verification without reloading WebView
    var extractedAuthToken by remember { mutableStateOf<String?>(null) }
    var extractedCt0 by remember { mutableStateOf<String?>(null) }

    // A separate state to trigger WebView reload after clearing cookies
    var webViewKey by remember { mutableStateOf(0) }

    val url = "https://x.com/i/flow/login"

    fun extractCookiesAndVerify() {
        if (isVerifying) return

        val cookieManager = CookieManager.getInstance()

        fun getCookiesForDomain(domain: String): Map<String, String>? {
            val cookiesStr = cookieManager.getCookie(domain)
            if (cookiesStr.isNullOrEmpty()) return null
            val cookieMap = cookiesStr.split(";").associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else parts[0].trim() to ""
            }
            if (!cookieMap["auth_token"].isNullOrEmpty() && !cookieMap["ct0"].isNullOrEmpty()) {
                return cookieMap
            }
            return null
        }

        val cookieMap = getCookiesForDomain("https://x.com")
            ?: getCookiesForDomain("https://twitter.com")

        if (cookieMap != null) {
            val authToken = cookieMap["auth_token"]
            val ct0 = cookieMap["ct0"]

            if (!authToken.isNullOrEmpty() && !ct0.isNullOrEmpty()) {
                extractedAuthToken = authToken
                extractedCt0 = ct0
                isVerifying = true
                verificationError = null

                scope.launch {
                    val result = apiClient.verifyCredentials(authToken, ct0)
                    isVerifying = false
                    when (result) {
                        is ApiResult.Success<*> -> {
                            authManager.saveCredentials(authToken, ct0)
                            // Navigate to home screen and clear backstack up to there to refresh it
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                        is ApiResult.Unauthorized -> {
                            verificationError = "未授權: ${result.message}"
                        }
                        is ApiResult.RateLimited -> {
                            verificationError = "限流: ${result.message}"
                        }
                        is ApiResult.NetworkError -> {
                            verificationError = "網路錯誤: ${result.message}"
                        }
                        is ApiResult.ParsingError -> {
                            verificationError = "解析錯誤: ${result.message}"
                        }
                    }
                }
            }
        }
    }

    fun retryVerification() {
        val authToken = extractedAuthToken
        val ct0 = extractedCt0
        if (authToken != null && ct0 != null) {
            isVerifying = true
            verificationError = null

            scope.launch {
                val result = apiClient.verifyCredentials(authToken, ct0)
                isVerifying = false
                when (result) {
                    is ApiResult.Success<*> -> {
                        authManager.saveCredentials(authToken, ct0)
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                    is ApiResult.Unauthorized -> {
                        verificationError = "未授權: ${result.message}"
                    }
                    is ApiResult.RateLimited -> {
                        verificationError = "限流: ${result.message}"
                    }
                    is ApiResult.NetworkError -> {
                        verificationError = "網路錯誤: ${result.message}"
                    }
                    is ApiResult.ParsingError -> {
                        verificationError = "解析錯誤: ${result.message}"
                    }
                }
            }
        } else {
            verificationError = null
        }
    }

    LaunchedEffect(verificationError, isVerifying) {
        if (verificationError == null && !isVerifying) {
            while (true) {
                delay(1000)
                extractCookiesAndVerify()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登入 X") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "注意：可能不支援使用 Google 或 Apple 帳號直接登入，請使用使用者名稱與密碼，或驗證碼登入。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            isFocusable = true
                            isFocusableInTouchMode = true
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.userAgentString = android.webkit.WebSettings.getDefaultUserAgent(ctx)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    return false
                                }
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        verificationError = "網頁載入錯誤: ${error?.description}"
                                    }
                                }
                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: android.webkit.WebResourceResponse?
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    if (request?.isForMainFrame == true) {
                                        verificationError = "HTTP 錯誤: ${errorResponse?.statusCode}"
                                    }
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    update = { view ->
                        // Only reload when explicitly triggered via webViewKey
                        if (view.tag != webViewKey) {
                            view.tag = webViewKey
                            view.loadUrl(url)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { extractCookiesAndVerify() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("我已完成登入，重新檢查")
                }
            }

            if (isVerifying) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("驗證中... (Verifying...)")
                        }
                    }
                }
            } else if (verificationError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "❌ 發生錯誤\n$verificationError",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (verificationError?.contains("未授權") == false) {
                                    Button(onClick = { retryVerification() }) {
                                        Text("重新檢查")
                                    }
                                }
                                Button(onClick = {
                                    CookieManager.getInstance().removeAllCookies {
                                        CookieManager.getInstance().flush()
                                        verificationError = null
                                        extractedAuthToken = null
                                        extractedCt0 = null
                                        webViewKey++ // Force WebView reload
                                    }
                                }) {
                                    Text("重新載入")
                                }
                                OutlinedButton(onClick = { navController.popBackStack() }) {
                                    Text("取消")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
