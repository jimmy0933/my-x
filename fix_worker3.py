with open('app/src/main/java/com/example/xdownloader/worker/DownloadWorker.kt', 'r') as f:
    content = f.read()

content = content.replace("import kotlinx.coroutines.!isStopped", "")
content = content.replace("import kotlinx.coroutines.isStopped", "")

with open('app/src/main/java/com/example/xdownloader/worker/DownloadWorker.kt', 'w') as f:
    f.write(content)
