with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

import re

# Fix audio playlist fetch error handling
content = content.replace("""            val audioPlaylist = resolved.audioPlaylistUrl?.let { audioUrl ->
                fetchUrlText(audioUrl)?.let { content ->
                    parseMediaPlaylist(content, audioUrl)
                }
            }""", """            val audioPlaylist = if (resolved.audioPlaylistUrl != null) {
                val content = fetchUrlText(resolved.audioPlaylistUrl) ?: throw Exception("Failed to fetch audio playlist")
                parseMediaPlaylist(content, resolved.audioPlaylistUrl)
            } else null""")

content = content.replace("""                        val bytes = fetchSegmentBytes(initSeg)
                        if (bytes != null) fmp4Demuxer.demuxInit(bytes)""", """                        val bytes = fetchSegmentBytes(initSeg) ?: throw Exception("Failed to fetch audio init segment")
                        fmp4Demuxer.demuxInit(bytes)""")

content = content.replace("""                        val bytes = fetchSegmentBytes(initSeg)
                        if (bytes != null) tsDemuxer.demux(bytes)""", """                        val bytes = fetchSegmentBytes(initSeg) ?: throw Exception("Failed to fetch audio init segment")
                        tsDemuxer.demux(bytes)""")

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'w') as f:
    f.write(content)
