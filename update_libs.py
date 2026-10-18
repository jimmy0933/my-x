import re

with open('gradle/libs.versions.toml', 'r') as f:
    content = f.read()

# Add new versions
versions = """
room = "2.6.1"
okhttp = "4.12.0"
media3 = "1.3.1"
work = "2.9.0"
gson = "2.10.1"
paging = "3.2.1"
ksp = "1.9.23-1.0.20"
navigation = "2.7.7"
"""
content = re.sub(r'(\[versions\]\n)', r'\1' + versions, content)

# Add new libraries
libraries = """
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-paging = { group = "androidx.room", name = "room-paging", version.ref = "room" }

okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging-interceptor = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }

work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }

gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }

paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }

navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
"""
content = re.sub(r'(\[libraries\]\n)', r'\1' + libraries, content)

# Add new plugins
plugins = """
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
"""
content = re.sub(r'(\[plugins\]\n)', r'\1' + plugins, content)

with open('gradle/libs.versions.toml', 'w') as f:
    f.write(content)
