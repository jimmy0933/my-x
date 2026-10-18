with open('app/src/main/java/com/example/xdownloader/worker/DownloadWorker.kt', 'r') as f:
    content = f.read()

content = content.replace("HlsDownloader.downloadHlsStream(url, outputStream)", "HlsDownloader.downloadHlsStream(url, outputStream) { progress, total -> onProgress((progress * 100) / total) }")

with open('app/src/main/java/com/example/xdownloader/worker/DownloadWorker.kt', 'w') as f:
    f.write(content)
