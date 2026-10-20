package com.example.xdownloader.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.DownloadEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.Room
import java.io.File
import java.util.UUID
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class DownloadWorkerOutputTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

        // Inject into singleton using reflection to ensure Worker uses it
        try {
            val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, db)
        } catch (e: Exception) {}
    }

    @Test
    fun testOutputFailureResultsInWorkerFailure() = runBlocking {
        // Enqueue a download
        val url = "http://fake-test.com/image.png"
        val workId = UUID.randomUUID().toString()
        db.downloadDao().insert(DownloadEntity(url, "IMAGE", "Title", "PENDING", workId = workId))

        // Create the worker
        val inputData = Data.Builder()
            .putString("url", url)
            .putString("type", "IMAGE")
            .putString("title", "Title")
            .build()

        val worker = TestListenableWorkerBuilder<DownloadWorker>(
            context = context,
            inputData = inputData
        ).setId(UUID.fromString(workId)).build()

        // Run the worker. Since it's a fake URL, connection will fail.
        // It will eventually return Result.failure()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)

        // Verify DB state was updated to FAILED with the matching workId
        val updated = db.downloadDao().getDownload(url)
        assertEquals("FAILED", updated?.status)
    }
}
