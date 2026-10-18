with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {", """var showViewer by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { if (tweet.media.isNotEmpty()) showViewer = true }) {""")

content = content.replace('Text("包含 ${tweet.media.size} 個媒體", color = MaterialTheme.colorScheme.primary)', """Text("包含 ${tweet.media.size} 個媒體", color = MaterialTheme.colorScheme.primary)
        }
    }
    if (showViewer) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showViewer = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.example.xdownloader.ui.components.MediaViewer(tweet.media)
                IconButton(onClick = { showViewer = false }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }""")

content = content.replace("import androidx.compose.material3.*", "import androidx.compose.material3.*\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Close")

with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'w') as f:
    f.write(content)
