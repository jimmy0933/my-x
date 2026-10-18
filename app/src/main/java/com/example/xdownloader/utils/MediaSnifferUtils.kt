package com.example.xdownloader.utils

import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import java.util.UUID

object MediaSnifferUtils {
    fun generateUniqueFileName(item: MediaItem): String {
        val extension = if (item.type == MediaType.IMAGE) {
            if (item.url.contains("format=png")) ".png" else ".jpg"
        } else {
            ".mp4"
        }
        val prefix = if (item.type == MediaType.IMAGE) "Image_" else "Video_"
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        return "X_${prefix}${System.currentTimeMillis()}_$uuid$extension"
    }
}
