package com.example.xdownloader.worker

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.downloader.HlsDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.UUID
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.io.InputStream
import java.io.OutputStream


class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private val sharedClient = okhttp3.OkHttpClient()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val type = inputData.getString("type") ?: "IMAGE"
        val title = inputData.getString("title") ?: "Download"

        createNotificationChannel()
        val db = AppDatabase.getDatabase(context)
        val dao = db.downloadDao()

        try {
            setForeground(createForegroundInfo(title, 0))
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Failed to start foreground service", e)
            dao.updateProgress(url, "FAILED", 0)
            return@withContext Result.failure(workDataOf("error" to "Foreground service start failed"))
        }

        val workId = id.toString()
        dao.updateProgressIfWorkIdMatches(url, "DOWNLOADING", 0, workId)

        var lastProgressTime = 0L

        val success = if (url.contains(".m3u8")) {
            downloadHlsVideo(url, title) { progress ->
                val now = System.currentTimeMillis()
                if (now - lastProgressTime > 1000 || progress == 100) {
                    lastProgressTime = now
                    dao.updateProgressIfWorkIdMatches(url, "DOWNLOADING", progress, workId)
                    setForegroundAsync(createForegroundInfo(title, progress))
                }
            }
        } else {
            downloadDirectFile(url, type, title) { progress ->
                val now = System.currentTimeMillis()
                if (now - lastProgressTime > 1000 || progress == 100) {
                    lastProgressTime = now
                    dao.updateProgressIfWorkIdMatches(url, "DOWNLOADING", progress, workId)
                    setForegroundAsync(createForegroundInfo(title, progress))
                }
            }
        }

        if (success && !isStopped) {
            dao.updateProgressIfWorkIdMatches(url, "COMPLETED", 100, workId)
            Result.success()
        } else {
            if (isStopped) {
                dao.updateProgressIfWorkIdMatches(url, "CANCELLED", 0, workId)
            } else {
                dao.updateProgressIfWorkIdMatches(url, "FAILED", 0, workId)
            }
            Result.failure(workDataOf("error" to "Download failed or cancelled"))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("download_channel", "Downloads", NotificationManager.IMPORTANCE_LOW)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(title: String, progress: Int): androidx.work.ForegroundInfo {
        val notification = NotificationCompat.Builder(context, "download_channel")
            .setContentTitle("Downloading: $title")
            .setContentText(if (progress < 100) "Progress: $progress%" else "Completed")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        val id = title.hashCode()
        val notificationId = if(id == 0) 1 else id
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            androidx.work.ForegroundInfo(notificationId, notification)
        }
    }

    private suspend fun downloadDirectFile(urlStr: String, type: String, title: String, onProgress: suspend (Int) -> Unit): Boolean {
        var uri: Uri? = null
        var targetFile: File? = null
        var copied = 0L
        var length = 0L
        var isEof = false
        var closeSuccess = false

        try {
            val isImage = type == "IMAGE"
            val mimeType = if (isImage) {
                if (urlStr.contains("format=png")) "image/png"
                else if (urlStr.contains("format=webp")) "image/webp"
                else "image/jpeg"
            } else {
                "video/mp4"
            }

            val ext = if (isImage) {
                if (urlStr.contains("format=png")) ".png"
                else if (urlStr.contains("format=webp")) ".webp"
                else ".jpg"
            } else ".mp4"

            val fileName = "X_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}$ext"

            val client = sharedClient
            val request = Request.Builder().url(urlStr).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false

                length = response.body?.contentLength() ?: -1L
                val inputStream = response.body?.byteStream() ?: return false

                var outputStream: java.io.OutputStream? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val collection = if (isImage) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                                     else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isImage) "${Environment.DIRECTORY_PICTURES}/XDownloader" else "${Environment.DIRECTORY_MOVIES}/XDownloader")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val contentResolver = context.contentResolver
                    uri = contentResolver.insert(collection, contentValues)
                    if (uri != null) {
                        outputStream = contentResolver.openOutputStream(uri!!)
                    }
                } else {
                    val targetDir = File(Environment.getExternalStoragePublicDirectory(if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES), "XDownloader")
                    if (!targetDir.exists()) targetDir.mkdirs()
                    targetFile = File(targetDir, fileName)
                    outputStream = targetFile!!.outputStream()
                }

                outputStream?.use { out ->
                    copied = 0L
                    val buffer = ByteArray(8 * 1024)
                    var bytes = inputStream.read(buffer)
                    while (bytes >= 0) {
                        if (isStopped) break // Check cancellation
                        out.write(buffer, 0, bytes)
                        copied += bytes
                        if (length > 0L) onProgress(((copied * 100) / length).toInt())
                        bytes = inputStream.read(buffer)
                    }
                    if (bytes < 0) isEof = true
                    out.flush()
                    closeSuccess = true
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            val downloadSuccess = !isStopped && copied > 0 && isEof && closeSuccess
            val localUri = uri
            val localTargetFile = targetFile

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && localUri != null) {
                val contentResolver = context.contentResolver
                if (downloadSuccess) {
                    val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    contentResolver.update(localUri, contentValues, null, null)
                } else {
                    contentResolver.delete(localUri, null, null)
                }
            } else if (localTargetFile != null && localTargetFile.exists()) {
                if (!downloadSuccess) {
                    localTargetFile.delete()
                }
            }
        }
        return !isStopped && copied > 0 && isEof && closeSuccess
    }

    private suspend fun downloadHlsVideo(url: String, title: String, onProgress: suspend (Int) -> Unit): Boolean {
        var uri: Uri? = null
        var targetFile: File? = null
        var outputStream: OutputStream? = null
        var success = false
        var closeSuccess = false
        try {
            val fileName = "X_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}.mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/XDownloader")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val contentResolver = context.contentResolver
                uri = contentResolver.insert(collection, contentValues) ?: return false
                outputStream = contentResolver.openOutputStream(uri)
            } else {
                val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "XDownloader")
                if (!targetDir.exists()) targetDir.mkdirs()
                targetFile = File(targetDir, fileName)
                outputStream = targetFile.outputStream()
            }

            if (outputStream == null) return false

            success = try {
                HlsDownloader.downloadHlsStream(url, outputStream) { progress, total -> kotlinx.coroutines.runBlocking {
                    if (isStopped) throw Exception("Cancelled")
                    onProgress((progress * 100) / total) }
                }
            } catch (e: Exception) {
                Log.e("DownloadWorker", "HLS Error", e)
                false
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                outputStream?.flush()
                outputStream?.close()
                closeSuccess = true
            } catch (e: Exception) {}

            val isComplete = success && !isStopped && closeSuccess

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                val contentResolver = context.contentResolver
                if (isComplete) {
                    val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    contentResolver.update(uri, contentValues, null, null)
                } else {
                    contentResolver.delete(uri, null, null)
                }
            } else if (targetFile != null && targetFile.exists()) {
                if (!isComplete) {
                    targetFile.delete()
                }
            }
        }
        return success && !isStopped && closeSuccess
    }
}
