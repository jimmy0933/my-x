package com.example.xdownloader.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestWorkerBuilder
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.DownloadEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import androidx.room.Room

@RunWith(AndroidJUnit4::class)
class DownloadWorkerConcurrencyTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @Test
    fun testWorkerProgressUpdateConcurrency() = runBlocking {
        db.downloadDao().insert(DownloadEntity("http://fake.com", "IMAGE", "Title", "PENDING", workId = "test-work-id"))

        // dao.updateProgressIfWorkIdMatches test
        db.downloadDao().updateProgressIfWorkIdMatches("http://fake.com", "DOWNLOADING", 50, "test-work-id")

        val updated = db.downloadDao().getDownload("http://fake.com")
        assertEquals(50, updated?.progress)

        // Wrong workId should not update
        db.downloadDao().updateProgressIfWorkIdMatches("http://fake.com", "DOWNLOADING", 100, "wrong-work-id")
        val notUpdated = db.downloadDao().getDownload("http://fake.com")
        assertEquals(50, notUpdated?.progress) // Remains 50
    }
}
