package com.example.xdownloader.worker

import android.content.ContentValues
import android.content.Context
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val type = inputData.getString("type") ?: "IMAGE"
        val title = inputData.getString("title") ?: "Download"

        val db = AppDatabase.getDatabase(context)
        val dao = db.downloadDao()

        dao.updateProgress(url, "DOWNLOADING", 0)

        val success = if (url.contains(".m3u8")) {
            downloadHlsVideo(url, title) { progress ->
                // Wait to avoid spamming the DB, or launch unawaited
            }
        } else {
            downloadDirectFile(url, type, title) { progress ->
                // Progress
            }
        }

        if (success) {
            dao.updateProgress(url, "COMPLETED", 100)
            Result.success()
        } else {
            dao.updateProgress(url, "FAILED", 0)
            Result.failure(workDataOf("error" to "Download failed"))
        }
    }

    private suspend fun downloadDirectFile(urlStr: String, type: String, title: String, onProgress: (Int) -> Unit): Boolean {
        try {
            val isImage = type == "IMAGE"
            val mimeType = if (isImage) {
                if (urlStr.contains("format=png")) "image/png" else "image/jpeg"
            } else {
                "video/mp4"
            }

            val fileName = "X_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}" + if(isImage) ".jpg" else ".mp4"

            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false

            val length = connection.contentLength
            val inputStream = connection.inputStream

            var uri: Uri? = null
            val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (isImage) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                                 else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, if (isImage) "${Environment.DIRECTORY_PICTURES}/XDownloader" else "${Environment.DIRECTORY_MOVIES}/XDownloader")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val contentResolver = context.contentResolver
                uri = contentResolver.insert(collection, contentValues) ?: return false
                contentResolver.openOutputStream(uri)
            } else {
                val targetDir = File(Environment.getExternalStoragePublicDirectory(if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES), "XDownloader")
                if (!targetDir.exists()) targetDir.mkdirs()
                File(targetDir, fileName).outputStream()
            } ?: return false

            var copied = 0L
            val buffer = ByteArray(8 * 1024)
            var bytes = inputStream.read(buffer)
            while (bytes >= 0) {
                outputStream.write(buffer, 0, bytes)
                copied += bytes
                if (length > 0) onProgress(((copied * 100) / length).toInt())
                bytes = inputStream.read(buffer)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, contentValues, null, null)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun downloadHlsVideo(url: String, title: String, onProgress: (Int) -> Unit): Boolean {
        try {
            val fileName = "X_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}.mp4"
            var uri: Uri? = null

            val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/XDownloader")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val contentResolver = context.contentResolver
                uri = contentResolver.insert(collection, contentValues) ?: return false
                contentResolver.openOutputStream(uri)
            } else {
                val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "XDownloader")
                if (!targetDir.exists()) targetDir.mkdirs()
                File(targetDir, fileName).outputStream()
            } ?: return false

            val success = try {
                HlsDownloader.downloadHlsStream(url, outputStream) { progress, total -> onProgress((progress * 100) / total) }
            } catch (e: Exception) {
                Log.e("DownloadWorker", "HLS Error", e)
                false
            } finally {
                outputStream.flush()
                outputStream.close()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                val contentResolver = context.contentResolver
                if (success) {
                    val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    contentResolver.update(uri, contentValues, null, null)
                } else {
                    contentResolver.delete(uri, null, null)
                }
            }

            return success
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
