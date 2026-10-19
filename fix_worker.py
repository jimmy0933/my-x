with open('app/src/main/java/com/example/xdownloader/worker/DownloadWorker.kt', 'r') as f:
    content = f.read()

import re

# Add getForegroundInfo support and throttle progress
new_imports = """import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.io.InputStream
import java.io.OutputStream"""

content = content.replace("import java.util.UUID", "import java.util.UUID\n" + new_imports)

# Set up doWork to support cancellation check and getForegroundInfo
new_do_work = """    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val type = inputData.getString("type") ?: "IMAGE"
        val title = inputData.getString("title") ?: "Download"

        createNotificationChannel()
        setForeground(createForegroundInfo(title, 0))

        val db = AppDatabase.getDatabase(context)
        val dao = db.downloadDao()

        dao.updateProgress(url, "DOWNLOADING", 0)

        var lastProgressTime = 0L

        val success = if (url.contains(".m3u8")) {
            downloadHlsVideo(url, title) { progress ->
                val now = System.currentTimeMillis()
                if (now - lastProgressTime > 1000 || progress == 100) {
                    lastProgressTime = now
                    dao.updateProgress(url, "DOWNLOADING", progress)
                    setForegroundAsync(createForegroundInfo(title, progress))
                }
            }
        } else {
            downloadDirectFile(url, type, title) { progress ->
                val now = System.currentTimeMillis()
                if (now - lastProgressTime > 1000 || progress == 100) {
                    lastProgressTime = now
                    dao.updateProgress(url, "DOWNLOADING", progress)
                    setForegroundAsync(createForegroundInfo(title, progress))
                }
            }
        }

        if (success && isActive) {
            dao.updateProgress(url, "COMPLETED", 100)
            Result.success()
        } else {
            if (!isActive) {
                dao.updateProgress(url, "CANCELLED", 0)
            } else {
                dao.updateProgress(url, "FAILED", 0)
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
        return androidx.work.ForegroundInfo(if(id == 0) 1 else id, notification)
    }"""
content = re.sub(r"    override suspend fun doWork\(\): Result = withContext\(Dispatchers\.IO\) \{.*?Result\.failure\(workDataOf\(\"error\" to \"Download failed\"\)\)\n        \}\n    \}", new_do_work, content, flags=re.DOTALL)

# Add cancellation and resource cleanup checks to Direct Download
new_direct = """    private suspend fun downloadDirectFile(urlStr: String, type: String, title: String, onProgress: (Int) -> Unit): Boolean {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var uri: Uri? = null
        var targetFile: File? = null

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

            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false

            val length = connection.contentLength
            inputStream = connection.inputStream

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
                uri = contentResolver.insert(collection, contentValues) ?: return false
                outputStream = contentResolver.openOutputStream(uri)
            } else {
                val targetDir = File(Environment.getExternalStoragePublicDirectory(if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES), "XDownloader")
                if (!targetDir.exists()) targetDir.mkdirs()
                targetFile = File(targetDir, fileName)
                outputStream = targetFile.outputStream()
            }

            if (outputStream == null) return false

            var copied = 0L
            val buffer = ByteArray(8 * 1024)
            var bytes = inputStream.read(buffer)
            while (bytes >= 0) {
                if (!isActive) break // Check cancellation
                outputStream.write(buffer, 0, bytes)
                copied += bytes
                if (length > 0) onProgress(((copied * 100) / length).toInt())
                bytes = inputStream.read(buffer)
            }

            return isActive && copied > 0

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { outputStream?.flush() } catch (e: Exception) {}
            try { outputStream?.close() } catch (e: Exception) {}
            try { inputStream?.close() } catch (e: Exception) {}
            try { connection?.disconnect() } catch (e: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                val contentResolver = context.contentResolver
                if (isActive) {
                    val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    contentResolver.update(uri, contentValues, null, null)
                } else {
                    contentResolver.delete(uri, null, null)
                }
            } else if (!isActive && targetFile != null && targetFile.exists()) {
                targetFile.delete()
            }
        }
    }"""
content = re.sub(r"    private suspend fun downloadDirectFile\(.*?return false\n        \}\n    \}", new_direct, content, flags=re.DOTALL)

# Add cancellation and resource cleanup to HLS Download
new_hls = """    private suspend fun downloadHlsVideo(url: String, title: String, onProgress: (Int) -> Unit): Boolean {
        var uri: Uri? = null
        var targetFile: File? = null
        var outputStream: OutputStream? = null
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

            val success = try {
                HlsDownloader.downloadHlsStream(url, outputStream) { progress, total ->
                    if (!isActive) throw Exception("Cancelled")
                    onProgress((progress * 100) / total)
                }
            } catch (e: Exception) {
                Log.e("DownloadWorker", "HLS Error", e)
                false
            }

            return success && isActive
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { outputStream?.flush() } catch (e: Exception) {}
            try { outputStream?.close() } catch (e: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                val contentResolver = context.contentResolver
                if (isActive) {
                    val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    contentResolver.update(uri, contentValues, null, null)
                } else {
                    contentResolver.delete(uri, null, null)
                }
            } else if (!isActive && targetFile != null && targetFile.exists()) {
                targetFile.delete()
            }
        }
    }"""
content = re.sub(r"    private suspend fun downloadHlsVideo\(.*?return false\n        \}\n    \}", new_hls, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/xdownloader/worker/DownloadWorker.kt', 'w') as f:
    f.write(content)
