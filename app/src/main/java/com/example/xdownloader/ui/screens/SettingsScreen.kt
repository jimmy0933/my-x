package com.example.xdownloader.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("x_auth", Context.MODE_PRIVATE) }

    var authToken by remember { mutableStateOf(prefs.getString("auth_token", "") ?: "") }
    var ct0 by remember { mutableStateOf(prefs.getString("ct0", "") ?: "") }

    val scope = rememberCoroutineScope()

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
            Text("由於 X API 限制，請在電腦或瀏覽器登入 X，並複製 Cookies 中的 auth_token 與 ct0 貼上於此。", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = authToken,
                onValueChange = { authToken = it },
                label = { Text("auth_token") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ct0,
                onValueChange = { ct0 = it },
                label = { Text("ct0") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    prefs.edit()
                        .putString("auth_token", authToken)
                        .putString("ct0", ct0)
                        .apply()
                    Toast.makeText(context, "已儲存設定", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("儲存 (Save)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (authToken.isEmpty() || ct0.isEmpty()) {
                Text("⚠️ 缺少必要憑證 (Missing credentials)", color = MaterialTheme.colorScheme.error)
            } else {
                Text("✅ 憑證已設定 (Credentials set)", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
