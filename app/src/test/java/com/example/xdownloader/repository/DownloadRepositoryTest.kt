package com.example.xdownloader.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.DownloadEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: DownloadRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        // We can't inject DB to repository directly without reflection or changing code.
        // DownloadRepository has `private val db = AppDatabase.getDatabase(context)`
        // If it's a singleton, let's just use the context.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        repository = DownloadRepository(context)
    }

    @Test
    fun testEnqueueDownload_withFakePendingState() = runBlocking {
        // Just a basic test to ensure enqueue runs without throwing
        val item = MediaItem("id1", "http://test.com", "thumb", MediaType.VIDEO, "HD")
        repository.enqueueDownload(item)
        val dao = AppDatabase.getDatabase(context).downloadDao()
        val download = dao.getDownload(item.url)
        assertNotNull(download)
        assertEquals("PENDING", download?.status)
    }
}
