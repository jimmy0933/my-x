with open('app/src/main/java/com/example/xdownloader/ui/screens/SettingsScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("import com.example.xdownloader.api.XApiClient", "import com.example.xdownloader.api.XApiClient\nimport com.example.xdownloader.api.ApiResult")
if "import com.example.xdownloader.api.XApiClient" not in content:
    content = content.replace("import kotlinx.coroutines.launch", "import kotlinx.coroutines.launch\nimport com.example.xdownloader.api.XApiClient\nimport com.example.xdownloader.api.ApiResult\nimport androidx.compose.ui.text.input.PasswordVisualTransformation")

content = content.replace("var authToken by remember { mutableStateOf(prefs.getString(\"auth_token\", \"\") ?: \"\") }", """var authToken by remember { mutableStateOf(prefs.getString("auth_token", "") ?: "") }
    var ct0 by remember { mutableStateOf(prefs.getString("ct0", "") ?: "") }
    var verificationStatus by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val apiClient = remember { XApiClient(context) }""")
content = content.replace("var ct0 by remember { mutableStateOf(prefs.getString(\"ct0\", \"\") ?: \"\") }", "")

content = content.replace("""            OutlinedTextField(
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
            )""", """            OutlinedTextField(
                value = authToken,
                onValueChange = { authToken = it; verificationStatus = null },
                label = { Text("auth_token") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ct0,
                onValueChange = { ct0 = it; verificationStatus = null },
                label = { Text("ct0") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )""")

content = content.replace("""            Button(
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
            }""", """            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        prefs.edit()
                            .putString("auth_token", authToken)
                            .putString("ct0", ct0)
                            .apply()
                        Toast.makeText(context, "已儲存設定", Toast.LENGTH_SHORT).show()

                        scope.launch {
                            isVerifying = true
                            verificationStatus = "驗證中..."
                            val result = apiClient.verifyCredentials()
                            verificationStatus = when (result) {
                                is ApiResult.Success -> "✅ 驗證成功 (Verified)"
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
                    Text("儲存並驗證")
                }

                OutlinedButton(
                    onClick = {
                        authToken = ""
                        ct0 = ""
                        verificationStatus = null
                        prefs.edit().clear().apply()
                        Toast.makeText(context, "已清除憑證", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清除")
                }
            }""")

content = content.replace("""            if (authToken.isEmpty() || ct0.isEmpty()) {
                Text("⚠️ 缺少必要憑證 (Missing credentials)", color = MaterialTheme.colorScheme.error)
            } else {
                Text("✅ 憑證已設定 (Credentials set)", color = MaterialTheme.colorScheme.primary)
            }""", """            if (authToken.isEmpty() || ct0.isEmpty()) {
                Text("⚠️ 缺少必要憑證 (Missing credentials)", color = MaterialTheme.colorScheme.error)
            } else {
                Text("⚠️ 憑證已填寫，狀態：${verificationStatus ?: "未驗證"}")
            }""")

with open('app/src/main/java/com/example/xdownloader/ui/screens/SettingsScreen.kt', 'w') as f:
    f.write(content)
