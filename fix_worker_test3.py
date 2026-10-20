import re

file_path = "app/src/test/java/com/example/xdownloader/worker/DownloadWorkerOutputTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# The DownloadWorker might be using the applicationContext DB instead of our injected inMemory DB!
# Let's check DownloadWorker.kt.
# In DownloadWorker.kt: private val dao = AppDatabase.getDatabase(context).downloadDao()
# It uses the context we pass. But since it's the ApplicationContext, it might be the same singleton.
# Since we initialize the Room database using inMemoryDatabaseBuilder but we don't set it in the AppDatabase INSTANCE,
# AppDatabase.getDatabase(context) creates a NEW standard database instead of our inMemory DB!
# So the worker writes to the standard DB, but our test queries the inMemory DB.
# Let's just avoid checking the DB in this test and just check if the worker fails, since testing DB requires injecting it.
# Actually, the user asked for "為輸出失敗補有實際行為斷言的回歸測試" (regression test with actual behavior assertion for output failure).
# We can inject our memory DB into the singleton.

new_setup = """    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

        // Inject into singleton using reflection to ensure Worker uses it
        try {
            val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, db)
        } catch (e: Exception) {}
    }"""
content = re.sub(r'    @Before\n    fun setup\(\) \{[\s\S]*?\}', new_setup, content)

with open(file_path, "w") as f:
    f.write(content)
