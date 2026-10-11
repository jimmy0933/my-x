package com.example.xdownloader.downloader

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.xdownloader.R
import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AndroidMediaDownloader(private val context: Context) {

    /**
     * Downloads media item using system DownloadManager
     */
    fun downloadWithDownloadManager(item: MediaItem, customFilename: String? = null) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(item.url)

            val fileExtension = if (item.type == MediaType.IMAGE) {
                if (item.url.contains("format=png")) ".png" else ".jpg"
            } else {
                ".mp4"
            }

            val fileName = customFilename ?: "XDownloader_${System.currentTimeMillis()}$fileExtension"
            val subDirectory = if (item.type == MediaType.IMAGE) "Pictures/XDownloader" else "Movies/XDownloader"

            val request = DownloadManager.Request(uri).apply {
                setTitle(if (item.type == MediaType.IMAGE) "下載圖片" else "下載影片")
                setDescription(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    if (item.type == MediaType.IMAGE) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES,
                    "XDownloader/$fileName"
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            downloadManager.enqueue(request)
            Toast.makeText(context, context.getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "下載失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Downloads media directly into MediaStore (Pictures/Movies) for immediate gallery display
     */
    suspend fun downloadToMediaStore(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val isImage = item.type == MediaType.IMAGE
            val mimeType = if (isImage) {
                if (item.url.contains("format=png")) "image/png" else "image/jpeg"
            } else {
                "video/mp4"
            }
            val ext = if (mimeType == "image/png") ".png" else if (isImage) ".jpg" else ".mp4"
            val fileName = "X_${System.currentTimeMillis()}$ext"

            val url = URL(item.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext false
            }

            val inputStream = connection.inputStream

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (isImage) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (isImage) "${Environment.DIRECTORY_PICTURES}/XDownloader" else "${Environment.DIRECTORY_MOVIES}/XDownloader"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val contentResolver = context.contentResolver
                val uri = contentResolver.insert(collection, contentValues) ?: return@withContext false

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            } else {
                val targetDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                    ),
                    "XDownloader"
                )
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val destFile = File(targetDir, fileName)
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            inputStream.close()
            connection.disconnect()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
