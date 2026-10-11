package com.example.xdownloader.data

enum class MediaType {
    IMAGE,
    VIDEO
}

data class MediaItem(
    val id: String,
    val url: String,
    val thumbnailUrl: String,
    val type: MediaType,
    val quality: String = "HD",
    val title: String = ""
)
