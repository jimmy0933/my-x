package com.example.xdownloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xdownloader.api.TweetParser
import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.FavoriteEntity
import com.example.xdownloader.ui.components.MediaDownloadBottomSheet
import androidx.compose.material.icons.filled.Download
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }

    var favorites by remember { mutableStateOf<List<FavoriteEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        db.favoriteDao().getAllFavorites().collect {
            favorites = it
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("最愛 (Favorites)") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (favorites.isEmpty()) {
                Text("沒有收藏任何貼文 (No favorites)", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(favorites) { fav ->
                        FavoriteCard(fav)
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteCard(fav: FavoriteEntity) {
    var showViewer by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)

    val urls = fav.mediaUrls.split(",").filter { it.isNotEmpty() }
    val types = fav.mediaTypes.split(",").filter { it.isNotEmpty() }

    val mediaItems = urls.indices.map {
        MediaItem(
            id = fav.tweetId + "_$it",
            url = urls[it],
            thumbnailUrl = "",
            type = if (types.getOrNull(it) == "VIDEO") MediaType.VIDEO else MediaType.IMAGE
        )
    }

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { if (mediaItems.isNotEmpty()) showViewer = true }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${fav.authorName} (@${fav.authorScreenName})", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = {
                    scope.launch {
                        db.favoriteDao().deleteFavorite(fav)
                        android.widget.Toast.makeText(context, "Removed from favorites", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Remove")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(fav.text, style = MaterialTheme.typography.bodyMedium)
            if (fav.deleted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("⚠️ 此貼文已被刪除或無法存取", color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { showDownloadSheet = true }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Download, contentDescription = "Download")
                }
            }
        }
    }

    if (showDownloadSheet && mediaItems.isNotEmpty()) {
        MediaDownloadBottomSheet(mediaItems = mediaItems, onDismiss = { showDownloadSheet = false })
    }

    if (showViewer && mediaItems.isNotEmpty()) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showViewer = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.example.xdownloader.ui.components.MediaViewer(mediaItems)
                IconButton(onClick = { showViewer = false }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}
