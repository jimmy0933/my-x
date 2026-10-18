import re

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'r') as f:
    content = f.read()

# Replace mdat Baos with direct outputStream writes to avoid OOM
replacement = """
            var currentMdatOffset = currentFileOffset + 16L
            for (tm in trackMetas) {
                for (s in tm.sampleMetas) {
                    s.offset = currentMdatOffset
                    currentMdatOffset += s.size
                }
            }

            val totalMdatSize = currentMdatOffset - currentFileOffset

            val mdatHeaderDos = java.io.DataOutputStream(outputStream)
            mdatHeaderDos.writeInt(1)
            mdatHeaderDos.write("mdat".toByteArray(Charsets.ISO_8859_1))
            mdatHeaderDos.writeLong(totalMdatSize)

            for (tm in trackMetas) {
                for (s in tm.sampleMetas) {
                    outputStream.write(s.data)
                }
            }
"""

content = re.sub(
    r'val mdatBaos = java\.io\.ByteArrayOutputStream\(\)[\s\S]*?outputStream\.write\(mdatPayload\)',
    replacement,
    content
)

with open('app/src/main/java/com/example/xdownloader/downloader/HlsDownloader.kt', 'w') as f:
    f.write(content)
