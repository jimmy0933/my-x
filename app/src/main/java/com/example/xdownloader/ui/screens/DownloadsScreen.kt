package com.example.xdownloader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.DownloadEntity
import com.example.xdownloader.repository.DownloadRepository
import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val repo = remember { DownloadRepository(context) }

    var downloads by remember { mutableStateOf<List<DownloadEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        db.downloadDao().getAllDownloads().collect {
            downloads = it
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("下載 (Downloads)") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (downloads.isEmpty()) {
                Text("沒有下載紀錄 (No downloads)", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(downloads) { dl ->
                        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(dl.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("狀態: ${dl.status}", style = MaterialTheme.typography.bodyMedium)
                                    if (dl.status == "DOWNLOADING" || dl.status == "PENDING") {
                                        TextButton(onClick = {
                                            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                                            scope.launch {
                                                repo.cancelDownload(dl.url)
                                            }
                                        }) {
                                            Text("取消 (Cancel)")
                                        }
                                    } else if (dl.status == "FAILED" || dl.status == "CANCELLED") {
                                        TextButton(onClick = {
                                            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                                            scope.launch {
                                                val mediaItem = MediaItem(
                                                    id = dl.title.removePrefix("Download "),
                                                    url = dl.url,
                                                    thumbnailUrl = "",
                                                    type = if (dl.type == "IMAGE") MediaType.IMAGE else MediaType.VIDEO,
                                                    quality = "Original"
                                                )
                                                repo.enqueueDownload(mediaItem, forceRetry = true)
                                            }
                                        }) {
                                            Text("重試 (Retry)")
                                        }
                                    }
                                }
                                if (dl.status == "DOWNLOADING" || dl.status == "PENDING") {
                                    LinearProgressIndicator(progress = { dl.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
