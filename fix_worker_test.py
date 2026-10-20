import re

file_path = "app/src/test/java/com/example/xdownloader/worker/DownloadWorkerOutputTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# DownloadWorker is a CoroutineWorker, so we must use TestListenableWorkerBuilder
content = content.replace("import androidx.work.testing.TestWorkerBuilder", "import androidx.work.testing.TestListenableWorkerBuilder")
content = content.replace("val worker = TestWorkerBuilder<DownloadWorker>(", "val worker = TestListenableWorkerBuilder<DownloadWorker>(")

with open(file_path, "w") as f:
    f.write(content)
