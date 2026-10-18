with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'r') as f:
    content = f.read()

content = content.replace("val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current", "val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current")

with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'w') as f:
    f.write(content)
