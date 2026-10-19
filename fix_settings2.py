with open('app/src/main/java/com/example/xdownloader/ui/screens/SettingsScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('var verificationStatus by remember { mutableStateOf<String?>(null) }\n    var isVerifying by remember { mutableStateOf(false) }\n    val apiClient = remember { XApiClient(context) }', """var ct0 by remember { mutableStateOf(prefs.getString("ct0", "") ?: "") }
    var verificationStatus by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val apiClient = remember { XApiClient(context) }""")

content = content.replace("is ApiResult.Success ->", "is ApiResult.Success<*> ->")

with open('app/src/main/java/com/example/xdownloader/ui/screens/SettingsScreen.kt', 'w') as f:
    f.write(content)
