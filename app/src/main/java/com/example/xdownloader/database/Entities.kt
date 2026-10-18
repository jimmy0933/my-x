package com.example.xdownloader.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val tweetId: String,
    val authorName: String,
    val authorScreenName: String,
    val authorAvatar: String,
    val text: String,
    val createdAt: Long,
    val mediaUrls: String, // JSON array of urls
    val mediaTypes: String, // JSON array of types (IMAGE/VIDEO)
    val mediaThumbnails: String, // JSON array of thumbnails
    val deleted: Boolean = false,
    val deleteReason: String = ""
)

@Entity(tableName = "following")
data class FollowEntity(
    @PrimaryKey val authorId: String,
    val authorName: String,
    val authorScreenName: String,
    val authorAvatar: String,
    val isAppOnly: Boolean = true
)
