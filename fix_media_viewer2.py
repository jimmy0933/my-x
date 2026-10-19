with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'r') as f:
    content = f.read()

content = content.replace("androidx.lifecycle.compose.LocalLifecycleOwner.current", "androidx.lifecycle.compose.LocalLifecycleOwner.current") # Sometimes need proper compose artifact
content = content.replace("import androidx.lifecycle.compose.LocalLifecycleOwner", "")

with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'w') as f:
    f.write(content)
