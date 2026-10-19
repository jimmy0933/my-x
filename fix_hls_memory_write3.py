with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

import re

# Since modifying DemuxedSample missed some places where properties are used,
# let's just make it simpler without `dataOffset` and `dataSize` and keep `data` nullable and var.

new_track = """    data class DemuxedSample(
        var data: ByteArray?, // Might be null if written to file
        val ptsMs: Long,
        val dtsMs: Long,
        val isKeyFrame: Boolean = false
    ) {
        val size: Int get() = data?.size ?: 0
    }

    class DemuxedTrack(
        val isVideo: Boolean
    ) {
        val samples = mutableListOf<DemuxedSample>()
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        var audioSpecificConfig: ByteArray? = null
        var sampleRate: Int = 44100
        var channelCount: Int = 2
    }"""
content = re.sub(r"    data class DemuxedSample\([\s\S]*?channelCount: Int = 2\n    \}", new_track, content)

# Restore sample creation
content = content.replace("targetTrack.samples.add(DemuxedSample(samplePayload, 0L, samplePayload.size, ptsMs, dtsMs, isKeyFrame))", "targetTrack.samples.add(DemuxedSample(samplePayload, ptsMs, dtsMs, isKeyFrame))")
content = content.replace("videoTrack.samples.add(DemuxedSample(frameData, 0L, frameData.size, ptsMs, dtsMs, isKeyFrame))", "videoTrack.samples.add(DemuxedSample(frameData, ptsMs, dtsMs, isKeyFrame))")
content = content.replace("audioTrack.samples.add(DemuxedSample(payload, 0L, payload.size, samplePts, samplePts, true))", "audioTrack.samples.add(DemuxedSample(payload, samplePts, samplePts, true))")


# Fix write step
replacement = """            var currentFileOffset = ftypBytes.size.toLong() + 16L
            val trackMetas = mutableListOf<TrackMeta>()

            var totalDataSize = 0L
            for (trIndex in tracks.indices) {
                val tr = tracks[trIndex]
                for (s in tr.samples) {
                    totalDataSize += s.size
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
                    val writeSize = s.size
                    if (dataToWrite != null) {
                        outputStream.write(dataToWrite)
                    }
                    currentFileOffset += writeSize

                    sampleMetas.add(SampleMeta(sampleOffset, writeSize, dtsDelta, ctsOffset, s.isKeyFrame))

                    // Allow GC of sample data after written
                    s.data = null
                }

                val trackDuration = if (samples.isNotEmpty()) trackMaxDts - trackMinDts + 40L else 0L
                trackMetas.add(TrackMeta(tr, trIndex + 1, sampleMetas, trackDuration))
            }
"""

content = re.sub(
    r"            var currentFileOffset = ftypBytes\.size\.toLong\(\) \+ 16L[\s\S]*?s\.data = null\n                \}\n\n                val trackDuration = if \(samples\.isNotEmpty\(\)\) trackMaxDts - trackMinDts \+ 40L else 0L\n                trackMetas\.add\(TrackMeta\(tr, trIndex \+ 1, sampleMetas, trackDuration\)\)\n            \}\n",
    replacement,
    content
)

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'w') as f:
    f.write(content)
