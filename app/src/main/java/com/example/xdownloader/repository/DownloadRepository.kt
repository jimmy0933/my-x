package com.example.xdownloader.repository

import android.content.Context
import androidx.room.withTransaction
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
        db.withTransaction {
            val existing = db.downloadDao().getDownload(item.url)
            var shouldProceed = forceRetry

            if (!forceRetry && existing != null && (existing.status == "PENDING" || existing.status == "DOWNLOADING")) {
                // Check if the work is actually running/enqueued in WorkManager
                if (existing.workId.isNotEmpty()) {
                    try {
                        val workInfo = workManager.getWorkInfoById(UUID.fromString(existing.workId)).get()
                        if (workInfo != null && (workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING)) {
                            // Actually running or enqueued
                        } else {
                            // Fake pending, we can restart
                            shouldProceed = true
                        }
                    } catch (e: Exception) {
                        // WorkId might be invalid or something else
                        shouldProceed = true
                    }
                } else {
                    shouldProceed = true
                }
            } else if (existing == null) {
                shouldProceed = true
            }

            if (!shouldProceed) {
                return@withTransaction // Already in progress
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
    }

    suspend fun cancelDownload(url: String) = withContext(Dispatchers.IO) {
        val existing = db.downloadDao().getDownload(url)
        if (existing != null && existing.workId.isNotEmpty()) {
            val op = workManager.cancelUniqueWork("dl_$url")
            try {
                op.result.get()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            db.downloadDao().updateProgressIfWorkIdMatches(url, "CANCELLED", 0, existing.workId)
        } else {
            // Fallback for extremely old jobs without workId
            val op = workManager.cancelUniqueWork("dl_$url")
            try {
                op.result.get()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            db.downloadDao().updateProgress(url, "CANCELLED", 0)
        }
    }
}
