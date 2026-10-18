with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

import re
content = content.replace("val firstSegmentBytes = firstSegment?.let { fetchSegmentBytes(it) }", "val firstSegmentBytes = firstSegment?.let { fetchSegmentBytes(it) ?: throw Exception(\"Failed to fetch first segment\") }")
content = content.replace("if (initBytes != null) fmp4Demuxer.demuxInit(initBytes)", "fmp4Demuxer.demuxInit(initBytes)")
content = content.replace("if (firstSegmentBytes != null) {\n                        fmp4Demuxer.demuxSegment(firstSegmentBytes)\n                        downloadedSegments++\n                        onProgress?.invoke(downloadedSegments, totalSegments)\n                    }", """                    fmp4Demuxer.demuxSegment(firstSegmentBytes!!)
                    downloadedSegments++
                    onProgress?.invoke(downloadedSegments, totalSegments)""")

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'w') as f:
    f.write(content)
