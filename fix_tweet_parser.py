with open('app/src/main/java/com/example/xdownloader/api/TweetParser.kt', 'r') as f:
    content = f.read()

import_str = "import java.text.SimpleDateFormat\nimport java.util.Locale\nimport com.google.gson.JsonObject"
content = content.replace("import com.google.gson.JsonObject", import_str)

date_parse_str = """            val createdAtStr = legacy.get("created_at")?.asString
            var createdAt = 0L
            if (createdAtStr != null) {
                try {
                    val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
                    val date = sdf.parse(createdAtStr)
                    if (date != null) createdAt = date.time
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }"""
content = content.replace("val createdAt = 0L", date_parse_str)

with open('app/src/main/java/com/example/xdownloader/api/TweetParser.kt', 'w') as f:
    f.write(content)
