package com.example.xdownloader

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.xdownloader.database.AppDatabase
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        // Manually create db and insert data
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config1 = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`url` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `status` TEXT NOT NULL, `progress` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `error` TEXT NOT NULL, PRIMARY KEY(`url`))")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val db1 = factory.create(config1).writableDatabase
        db1.execSQL("INSERT INTO downloads (url, type, title, status, progress, filePath, error) VALUES ('http://test.com', 'IMAGE', 'Test Title', 'PENDING', 0, '', '')")
        db1.close()

        val config2 = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    AppDatabase.MIGRATION_1_2.migrate(db)
                }
            })
            .build()

        val db2 = factory.create(config2).writableDatabase

        val cursor = db2.query("SELECT * FROM downloads WHERE url = 'http://test.com'")
        assert(cursor.moveToFirst())
        val workIdIndex = cursor.getColumnIndex("workId")
        assert(workIdIndex != -1)
        val workId = cursor.getString(workIdIndex)
        assert(workId == "")
        cursor.close()
        db2.close()
    }
}
