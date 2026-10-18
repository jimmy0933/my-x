with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

# Check if my previous fix_hls.py already did this, if so this step might be mostly complete.
# We need to make sure we return false or throw if retries fail.
content = content.replace("fetchSegmentBytes(seg) ?: return false", "fetchSegmentBytes(seg) ?: throw Exception(\"Failed to fetch segment\")")
content = content.replace("fetchSegmentBytes(initSeg) ?: return false", "fetchSegmentBytes(initSeg) ?: throw Exception(\"Failed to fetch init segment\")")
content = content.replace("if (initBytes == null) return false", "if (initBytes == null) throw Exception(\"Failed to fetch init segment\")")

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'w') as f:
    f.write(content)
