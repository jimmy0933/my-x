with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

import re
content = content.replace("if (initBytes != null) tsDemuxer.demux(initBytes)", "tsDemuxer.demux(initBytes)")
content = content.replace("""                    if (firstSegmentBytes != null) {
                        tsDemuxer.demux(firstSegmentBytes)
                        downloadedSegments++
                        onProgress?.invoke(downloadedSegments, totalSegments)
                    }""", """                    tsDemuxer.demux(firstSegmentBytes!!)
                    downloadedSegments++
                    onProgress?.invoke(downloadedSegments, totalSegments)""")

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'w') as f:
    f.write(content)
