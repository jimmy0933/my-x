package com.example.xdownloader.repository

import android.content.Context
import androidx.work.*
import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.DownloadEntity
import com.example.xdownloader.worker.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DownloadRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val workManager = WorkManager.getInstance(context)

    suspend fun enqueueDownload(item: MediaItem, forceRetry: Boolean = false) = withContext(Dispatchers.IO) {
        val existing = db.downloadDao().getDownload(item.url)
        if (!forceRetry && existing != null && (existing.status == "PENDING" || existing.status == "DOWNLOADING")) {
            return@withContext // Already in progress
        }

        val workId = UUID.randomUUID().toString()
        val typeStr = if (item.type == MediaType.IMAGE) "IMAGE" else "VIDEO"
        val titleStr = "Download ${item.id}"

        db.downloadDao().insert(
            DownloadEntity(
                url = item.url,
                type = typeStr,
                title = titleStr,
                status = "PENDING",
                workId = workId
            )
        )

        val data = Data.Builder()
            .putString("url", item.url)
            .putString("type", typeStr)
            .putString("title", titleStr)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(UUID.fromString(workId))
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        workManager.enqueueUniqueWork("dl_${item.url}", ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun cancelDownload(url: String) = withContext(Dispatchers.IO) {
        workManager.cancelUniqueWork("dl_$url")
        db.downloadDao().updateProgress(url, "CANCELLED", 0)
    }
}
