import re

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

content = content.replace("val initBytes = if (initSeg == firstSegment) firstSegmentBytes else fetchSegmentBytes(initSeg)",
"""val initBytes = if (initSeg == firstSegment) firstSegmentBytes else fetchSegmentBytes(initSeg)
                    if (initBytes == null) return false""")

content = content.replace("""val segBytes = fetchSegmentBytes(seg)
                    if (segBytes != null) {
                        fmp4Demuxer.demuxSegment(segBytes)
                    }""", """val segBytes = fetchSegmentBytes(seg) ?: return false
                    fmp4Demuxer.demuxSegment(segBytes)""")

content = content.replace("""val audioInitBytes = fetchSegmentBytes(initSeg)
                        if (audioInitBytes != null) audioDemuxer.demuxInit(audioInitBytes)""",
"""val audioInitBytes = fetchSegmentBytes(initSeg) ?: return false
                        audioDemuxer.demuxInit(audioInitBytes)""")

content = content.replace("""val segBytes = fetchSegmentBytes(seg)
                        if (segBytes != null) {
                            audioDemuxer.demuxSegment(segBytes)
                        }""", """val segBytes = fetchSegmentBytes(seg) ?: return false
                        audioDemuxer.demuxSegment(segBytes)""")

content = content.replace("""val segBytes = fetchSegmentBytes(seg)
                    if (segBytes != null) {
                        tsDemuxer.demuxSegment(segBytes)
                    }""", """val segBytes = fetchSegmentBytes(seg) ?: return false
                    tsDemuxer.demuxSegment(segBytes)""")

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'w') as f:
    f.write(content)
