import re

def update_file(filename, search, replace):
    with open(filename, 'r') as f:
        content = f.read()
    content = content.replace(search, replace)
    with open(filename, 'w') as f:
        f.write(content)

# Update HomeScreen.kt
with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'r') as f:
    hs_content = f.read()

if "import com.example.xdownloader.ui.components.MediaDownloadBottomSheet" not in hs_content:
    hs_content = hs_content.replace(
        "import kotlinx.coroutines.launch",
        "import kotlinx.coroutines.launch\nimport com.example.xdownloader.ui.components.MediaDownloadBottomSheet\nimport androidx.compose.material.icons.filled.Download"
    )

if "var showDownloadSheet by remember { mutableStateOf(false) }" not in hs_content:
    hs_content = hs_content.replace(
        "val db = AppDatabase.getDatabase(context)",
        "val db = AppDatabase.getDatabase(context)\n    var showDownloadSheet by remember { mutableStateOf(false) }"
    )

if "showDownloadSheet = true" not in hs_content:
    hs_content = hs_content.replace(
        """Icon(androidx.compose.material.icons.Icons.Default.Favorite, contentDescription = "Favorite")
                }""",
        """Icon(androidx.compose.material.icons.Icons.Default.Favorite, contentDescription = "Favorite")
                }
                IconButton(onClick = { showDownloadSheet = true }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Download, contentDescription = "Download")
                }"""
    )

if "if (showDownloadSheet)" not in hs_content:
    hs_content = hs_content.replace(
        """    if (showViewer) {""",
        """    if (showDownloadSheet) {
        MediaDownloadBottomSheet(mediaItems = tweet.media, onDismiss = { showDownloadSheet = false })
    }

    if (showViewer) {"""
    )

with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'w') as f:
    f.write(hs_content)

# Update FavoritesScreen.kt
with open('app/src/main/java/com/example/xdownloader/ui/screens/FavoritesScreen.kt', 'r') as f:
    fs_content = f.read()

if "import com.example.xdownloader.ui.components.MediaDownloadBottomSheet" not in fs_content:
    fs_content = fs_content.replace(
        "import com.example.xdownloader.database.FavoriteEntity",
        "import com.example.xdownloader.database.FavoriteEntity\nimport com.example.xdownloader.ui.components.MediaDownloadBottomSheet\nimport androidx.compose.material.icons.filled.Download"
    )

if "var showDownloadSheet by remember { mutableStateOf(false) }" not in fs_content:
    fs_content = fs_content.replace(
        "var showViewer by remember { mutableStateOf(false) }",
        "var showViewer by remember { mutableStateOf(false) }\n    var showDownloadSheet by remember { mutableStateOf(false) }"
    )

if "showDownloadSheet = true" not in fs_content:
    fs_content = fs_content.replace(
        """Text(fav.text, style = MaterialTheme.typography.bodyMedium)""",
        """Text(fav.text, style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { showDownloadSheet = true }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Download, contentDescription = "Download")
                }
            }"""
    )

if "if (showDownloadSheet)" not in fs_content:
    fs_content = fs_content.replace(
        """    if (showViewer && mediaItems.isNotEmpty()) {""",
        """    if (showDownloadSheet && mediaItems.isNotEmpty()) {
        MediaDownloadBottomSheet(mediaItems = mediaItems, onDismiss = { showDownloadSheet = false })
    }

    if (showViewer && mediaItems.isNotEmpty()) {"""
    )

with open('app/src/main/java/com/example/xdownloader/ui/screens/FavoritesScreen.kt', 'w') as f:
    f.write(fs_content)
