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
    var showWebView by remember { mutableStateOf(true) }

    // Store these so we can retry verification without reloading WebView
    var extractedAuthToken by remember { mutableStateOf<String?>(null) }
    var extractedCt0 by remember { mutableStateOf<String?>(null) }

    val url = "https://twitter.com/i/flow/login"

    fun extractCookiesAndVerify() {
        if (isVerifying) return

        val cookieManager = CookieManager.getInstance()
        var cookies = cookieManager.getCookie("https://twitter.com")
        if (cookies.isNullOrEmpty()) {
            cookies = cookieManager.getCookie("https://x.com")
        }

        if (cookies != null) {
            val cookieMap = cookies.split(";").associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else parts[0].trim() to ""
            }

            val authToken = cookieMap["auth_token"]
            val ct0 = cookieMap["ct0"]

            if (!authToken.isNullOrEmpty() && !ct0.isNullOrEmpty()) {
                extractedAuthToken = authToken
                extractedCt0 = ct0
                showWebView = false
                isVerifying = true
                verificationError = null

                scope.launch {
                    val result = apiClient.verifyCredentials(authToken, ct0)
                    isVerifying = false
                    when (result) {
                        is ApiResult.Success<*> -> {
                            authManager.saveCredentials(authToken, ct0)
                            navController.popBackStack()
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
                        navController.popBackStack()
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
            showWebView = true
            verificationError = null
        }
    }

    LaunchedEffect(showWebView, verificationError) {
        if (showWebView && verificationError == null) {
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (isVerifying) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("驗證中... (Verifying...)")
                    }
                }
            } else if (verificationError != null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "❌ 驗證失敗\n$verificationError",
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
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                verificationError = null
                                showWebView = true
                                extractedAuthToken = null
                                extractedCt0 = null
                            }) {
                                Text("重新登入")
                            }
                            OutlinedButton(onClick = { navController.popBackStack() }) {
                                Text("取消")
                            }
                        }
                    }
                }
            } else if (showWebView) {
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
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return false
                                    }
                                }
                                loadUrl(url)
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
            }
        }
    }
}
