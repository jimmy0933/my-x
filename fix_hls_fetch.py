with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

import re

new_fetch = """    fun fetchSegmentBytes(segment: Segment): ByteArray? {
        var retries = 3
        while (retries > 0) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(segment.url)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                if (segment.byteRangeLength != null && segment.byteRangeOffset != null) {
                    val start = segment.byteRangeOffset
                    val end = segment.byteRangeOffset + segment.byteRangeLength - 1
                    connection.setRequestProperty("Range", "bytes=$start-$end")
                }

                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    val inputStream = connection.inputStream
                    if (responseCode == HttpURLConnection.HTTP_OK && segment.byteRangeLength != null) {
                        val start = segment.byteRangeOffset ?: 0L
                        var skipped = 0L
                        while (skipped < start) {
                            val s = inputStream.skip(start - skipped)
                            if (s <= 0) break
                            skipped += s
                        }
                    }

                    val length = segment.byteRangeLength ?: connection.contentLength.toLong()
                    val baos = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var read = inputStream.read(buffer)
                    var totalRead = 0L
                    while (read != -1) {
                        baos.write(buffer, 0, read)
                        totalRead += read
                        if (length > 0 && totalRead >= length) break
                        read = inputStream.read(buffer)
                    }
                    inputStream.close()
                    return baos.toByteArray()
                } else {
                    retries--
                    Thread.sleep(1000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                retries--
                Thread.sleep(1000)
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }"""

content = re.sub(r"    fun fetchSegmentBytes\(segment: Segment\): ByteArray\? \{.*?return null\n            \}\n        \}\n    \}", new_fetch, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'w') as f:
    f.write(content)
