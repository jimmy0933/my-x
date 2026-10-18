package com.example.xdownloader.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val url: String,
    val type: String, // "IMAGE" or "VIDEO"
    val title: String,
    val status: String, // "PENDING", "DOWNLOADING", "COMPLETED", "FAILED"
    val progress: Int = 0,
    val filePath: String = "",
    val error: String = ""
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY status DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE url = :url")
    suspend fun getDownload(url: String): DownloadEntity?

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE url = :url")
    suspend fun updateProgress(url: String, status: String, progress: Int)
}
