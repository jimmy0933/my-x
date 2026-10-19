import re

with open('app/src/main/res/xml/data_extraction_rules.xml', 'r') as f:
    xml = f.read()
xml = xml.replace('<exclude path="." />', '<exclude domain="sharedpref" path="." />')
with open('app/src/main/res/xml/data_extraction_rules.xml', 'w') as f:
    f.write(xml)

with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'r') as f:
    kt = f.read()

kt = kt.replace("@OptIn(ExperimentalFoundationApi::class)", "@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)\n@OptIn(ExperimentalFoundationApi::class)")
with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'w') as f:
    f.write(kt)
