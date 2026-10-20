import re

file_path = "app/src/test/java/com/example/xdownloader/worker/DownloadWorkerOutputTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# TestListenableWorkerBuilder doesn't take executor in constructor directly like that in some versions, or maybe it does, but we can just omit it
content = re.sub(r'executor = java\.util\.concurrent\.Executors\.newSingleThreadExecutor\(\),\s*', '', content)

with open(file_path, "w") as f:
    f.write(content)
