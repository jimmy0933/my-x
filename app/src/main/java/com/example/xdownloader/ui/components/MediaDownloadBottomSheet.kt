package com.example.xdownloader.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import coil.compose.rememberAsyncImagePainter
import com.example.xdownloader.R
import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.DownloadEntity
import com.example.xdownloader.worker.DownloadWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDownloadBottomSheet(
    mediaItems: List<MediaItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    var selectedFilter by remember { mutableStateOf<MediaType?>(null) }

    val filteredItems = remember(mediaItems, selectedFilter) {
        if (selectedFilter == null) mediaItems
        else mediaItems.filter { it.type == selectedFilter }
    }

    fun enqueueDownload(item: MediaItem) {
        scope.launch {
            db.downloadDao().insert(
                DownloadEntity(
                    url = item.url,
                    type = if (item.type == MediaType.IMAGE) "IMAGE" else "VIDEO",
                    title = "Download ${item.id}",
                    status = "PENDING"
                )
            )

            val workManager = WorkManager.getInstance(context)
            val data = Data.Builder()
                .putString("url", item.url)
                .putString("type", if (item.type == MediaType.IMAGE) "IMAGE" else "VIDEO")
                .putString("title", "Download ${item.id}")
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            workManager.enqueueUniqueWork("dl_${item.url}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.detected_media_title, filteredItems.size), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedFilter == null, onClick = { selectedFilter = null }, label = { Text("全部") })
                    FilterChip(selected = selectedFilter == MediaType.IMAGE, onClick = { selectedFilter = MediaType.IMAGE }, label = { Text("圖片") }, leadingIcon = { Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp)) })
                    FilterChip(selected = selectedFilter == MediaType.VIDEO, onClick = { selectedFilter = MediaType.VIDEO }, label = { Text("影片") }, leadingIcon = { Icon(Icons.Default.Movie, null, modifier = Modifier.size(18.dp)) })
                }
                if (filteredItems.isNotEmpty()) {
                    Button(onClick = {
                        filteredItems.forEach { enqueueDownload(it) }
                        Toast.makeText(context, "已加入下載佇列", Toast.LENGTH_SHORT).show()
                    }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.download_all), fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_media_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filteredItems, key = { it.id }) { item ->
                        MediaItemRow(item = item, onDownload = {
                            enqueueDownload(item)
                            Toast.makeText(context, "已加入下載", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun MediaItemRow(item: MediaItem, onDownload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background)) {
                Image(painter = rememberAsyncImagePainter(model = item.thumbnailUrl), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(4.dp)) {
                    Text(if (item.type == MediaType.IMAGE) stringResource(R.string.type_image) else stringResource(R.string.type_video), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.quality, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = onDownload, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.download_item))
            }
        }
    }
}
