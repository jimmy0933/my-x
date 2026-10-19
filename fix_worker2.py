with open('app/src/main/java/com/example/xdownloader/worker/DownloadWorker.kt', 'r') as f:
    content = f.read()

import re

# In DownloadWorker, `isActive` needs to refer to the CoroutineWorker's `isStopped` or we can just pass `coroutineContext.isActive`. Let's use `isStopped`.
# `setForegroundAsync` needs to be not inside another suspend context if it's called inside the lambda. The lambda is not suspend.
# So we need to launch it. But wait, `dao.updateProgress` is suspend! So the lambda MUST be a suspend function!

content = content.replace("private suspend fun downloadDirectFile(urlStr: String, type: String, title: String, onProgress: (Int) -> Unit)", "private suspend fun downloadDirectFile(urlStr: String, type: String, title: String, onProgress: suspend (Int) -> Unit)")
content = content.replace("private suspend fun downloadHlsVideo(url: String, title: String, onProgress: (Int) -> Unit)", "private suspend fun downloadHlsVideo(url: String, title: String, onProgress: suspend (Int) -> Unit)")
content = content.replace("HlsDownloader.downloadHlsStream(url, outputStream) { progress, total ->", "HlsDownloader.downloadHlsStream(url, outputStream) { progress, total -> kotlinx.coroutines.runBlocking {")
content = content.replace("onProgress((progress * 100) / total)\n                }", "onProgress((progress * 100) / total) } \n                }")
content = content.replace("!isActive", "isStopped")
content = content.replace("isActive", "!isStopped")

with open('app/src/main/java/com/example/xdownloader/worker/DownloadWorker.kt', 'w') as f:
    f.write(content)
