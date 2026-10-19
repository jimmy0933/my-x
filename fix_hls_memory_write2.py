with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

import re

# We need to remove the whole block relying on mdatBaos and rewrite it to just write to file directly.

replacement = """            var currentFileOffset = ftypBytes.size.toLong() + 16L
            val trackMetas = mutableListOf<TrackMeta>()

            var totalDataSize = 0L
            for (trIndex in tracks.indices) {
                val tr = tracks[trIndex]
                for (s in tr.samples) {
                    totalDataSize += s.dataSize
                }
            }

            outputStream.write(ftypBytes)

            val mdatHeaderDos = java.io.DataOutputStream(outputStream)
            mdatHeaderDos.writeInt(1)
            mdatHeaderDos.write("mdat".toByteArray(Charsets.ISO_8859_1))
            mdatHeaderDos.writeLong(totalDataSize + 16L)

            for (trIndex in tracks.indices) {
                val tr = tracks[trIndex]
                val sampleMetas = mutableListOf<SampleMeta>()

                val samples = if (tr.isVideo) {
                    tr.samples.sortedBy { if (it.dtsMs >= 0) it.dtsMs else it.ptsMs }
                } else {
                    tr.samples
                }

                var trackMinDts = Long.MAX_VALUE
                var trackMaxDts = 0L

                for (i in samples.indices) {
                    val s = samples[i]
                    val pts = Math.max(0L, s.ptsMs - minPts)
                    val dts = Math.max(0L, (if (s.dtsMs >= 0) s.dtsMs else s.ptsMs) - minPts)

                    if (dts < trackMinDts) trackMinDts = dts
                    if (dts > trackMaxDts) trackMaxDts = dts

                    val nextDts = if (i + 1 < samples.size) {
                        val nextS = samples[i + 1]
                        Math.max(0L, (if (nextS.dtsMs >= 0) nextS.dtsMs else nextS.ptsMs) - minPts)
                    } else dts + 40L

                    val dtsDelta = (nextDts - dts).toInt().coerceAtLeast(1)
                    val ctsOffset = (pts - dts).toInt()

                    val sampleOffset = currentFileOffset
                    val dataToWrite = s.data
                    if (dataToWrite != null) {
                        outputStream.write(dataToWrite, s.dataOffset.toInt(), s.dataSize)
                    }
                    currentFileOffset += s.dataSize

                    sampleMetas.add(SampleMeta(sampleOffset, s.dataSize, dtsDelta, ctsOffset, s.isKeyFrame))

                    // Allow GC of sample data after written
                    s.data = null
                }

                val trackDuration = if (samples.isNotEmpty()) trackMaxDts - trackMinDts + 40L else 0L
                trackMetas.add(TrackMeta(tr, trIndex + 1, sampleMetas, trackDuration))
            }
"""

content = re.sub(
    r"            var currentFileOffset = ftypBytes\.size\.toLong\(\) \+ 16L\n            val mdatBaos = java\.io\.ByteArrayOutputStream\(\)[\s\S]*?outputStream\.write\(mdatPayload\)\n",
    replacement,
    content
)

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'w') as f:
    f.write(content)
