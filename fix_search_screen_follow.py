with open('app/src/main/java/com/example/xdownloader/ui/screens/SearchAuthorScreen.kt', 'r') as f:
    content = f.read()

import re

# Add followed authors list to the Search Screen
new_section = """
            Spacer(modifier = Modifier.height(32.dp))

            Text("已追蹤作者 (僅此 App 內)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val following by db.followDao().getAllFollowing().collectAsState(initial = emptyList())
            if (following.isEmpty()) {
                Text("目前沒有追蹤任何人", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(following, key = { it.authorId }) { author ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("@${author.authorScreenName}", style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = {
                                    scope.launch {
                                        db.followDao().deleteFollow(author)
                                        Toast.makeText(context, "已取消追蹤 @${author.authorScreenName}", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Unfollow")
                                }
                            }
                        }
                    }
                }
            }"""

content = content.replace("            if (tweetData != null) {\n                Spacer(modifier = Modifier.height(16.dp))\n                TweetCard(tweetData!!)\n            }\n        }\n    }\n}", "            if (tweetData != null) {\n                Spacer(modifier = Modifier.height(16.dp))\n                TweetCard(tweetData!!)\n            }\n" + new_section + "\n        }\n    }\n}")
content = content.replace("import kotlinx.coroutines.launch", "import kotlinx.coroutines.launch\nimport androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Close")

with open('app/src/main/java/com/example/xdownloader/ui/screens/SearchAuthorScreen.kt', 'w') as f:
    f.write(content)
