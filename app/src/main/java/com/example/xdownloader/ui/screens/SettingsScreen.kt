package com.example.xdownloader.ui.screens

import android.content.Context
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.xdownloader.api.ApiResult
import com.example.xdownloader.api.AuthManager
import com.example.xdownloader.api.XApiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val authManager = remember { AuthManager.getInstance(context) }
    val isConfigured by authManager.authState.collectAsState()

    val apiClient = remember { XApiClient(context) }
    val scope = rememberCoroutineScope()

    var isVerifying by remember { mutableStateOf(false) }
    var verificationStatus by remember { mutableStateOf<String?>(null) }

    var showAdvanced by remember { mutableStateOf(false) }

    // Advanced settings states
    var manualAuthToken by remember { mutableStateOf(authManager.getAuthToken() ?: "") }
    var manualCt0 by remember { mutableStateOf(authManager.getCt0() ?: "") }

    LaunchedEffect(isConfigured) {
        if (isConfigured) {
            verificationStatus = "✅ 已登入"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("設定 (Settings)") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("X (Twitter) 登入憑證", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (!isConfigured) {
                Text("尚未登入 X，請點擊下方按鈕登入。", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate("login") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("登入 X")
                }
            } else {
                Text("狀態: ${verificationStatus ?: "未知"}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                isVerifying = true
                                verificationStatus = "驗證中..."
                                val result = apiClient.verifyCredentials()
                                verificationStatus = when (result) {
                                    is ApiResult.Success<*> -> "✅ 驗證成功 (@${result.data})"
                                    is ApiResult.Unauthorized -> "❌ 未授權: ${result.message}"
                                    is ApiResult.RateLimited -> "❌ 限流: ${result.message}"
                                    is ApiResult.NetworkError -> "❌ 網路錯誤: ${result.message}"
                                    is ApiResult.ParsingError -> "❌ 解析錯誤: ${result.message}"
                                }
                                isVerifying = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isVerifying
                    ) {
                        Text("重新檢查")
                    }

                    OutlinedButton(
                        onClick = {
                            authManager.clearCredentials()
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                            android.webkit.CookieManager.getInstance().flush()
                            navController.navigate("login")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("切換帳號")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        authManager.clearCredentials()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        verificationStatus = null
                        manualAuthToken = ""
                        manualCt0 = ""
                        Toast.makeText(context, "已登出並清除憑證", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("登出")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Advanced settings section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("進階設定 (手動輸入 Cookie)", style = MaterialTheme.typography.titleSmall)
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column {
                    Text("作為相容性備援，您可以手動輸入 auth_token 與 ct0。", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualAuthToken,
                        onValueChange = { manualAuthToken = it },
                        label = { Text("auth_token") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualCt0,
                        onValueChange = { manualCt0 = it },
                        label = { Text("ct0") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            authManager.saveCredentials(manualAuthToken, manualCt0)
                            Toast.makeText(context, "已儲存手動憑證", Toast.LENGTH_SHORT).show()

                            scope.launch {
                                isVerifying = true
                                verificationStatus = "驗證中..."
                                val result = apiClient.verifyCredentials()
                                verificationStatus = when (result) {
                                    is ApiResult.Success<*> -> "✅ 驗證成功 (@${result.data})"
                                    is ApiResult.Unauthorized -> "❌ 未授權: ${result.message}"
                                    is ApiResult.RateLimited -> "❌ 限流: ${result.message}"
                                    is ApiResult.NetworkError -> "❌ 網路錯誤: ${result.message}"
                                    is ApiResult.ParsingError -> "❌ 解析錯誤: ${result.message}"
                                }
                                isVerifying = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isVerifying
                    ) {
                        Text("儲存並驗證")
                    }
                }
            }
        }
    }
}
