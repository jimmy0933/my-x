import re

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

# Replace DemuxedSample and DemuxedTrack definitions
new_track = """    data class DemuxedSample(
        var data: ByteArray?, // Might be null if written to file
        var dataOffset: Long = 0L,
        val dataSize: Int,
        val ptsMs: Long,
        val dtsMs: Long,
        val isKeyFrame: Boolean = false
    )

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

# Change Fmp4Demuxer and TsDemuxer sample additions
content = content.replace("targetTrack.samples.add(DemuxedSample(samplePayload, ptsMs, dtsMs, isKeyFrame))", "targetTrack.samples.add(DemuxedSample(samplePayload, 0L, samplePayload.size, ptsMs, dtsMs, isKeyFrame))")
content = content.replace("videoTrack.samples.add(DemuxedSample(frameData, ptsMs, dtsMs, isKeyFrame))", "videoTrack.samples.add(DemuxedSample(frameData, 0L, frameData.size, ptsMs, dtsMs, isKeyFrame))")
content = content.replace("audioTrack.samples.add(DemuxedSample(payload, samplePts, samplePts, true))", "audioTrack.samples.add(DemuxedSample(payload, 0L, payload.size, samplePts, samplePts, true))")

# Change MP4 Muxer memory handling - write interleaved mdat out immediately
# We need to change how the final writing is done. It uses a big ByteArrayOutputStream inside packageMp4.
print("Need to adapt packageMp4 directly to disk instead of memory.")
