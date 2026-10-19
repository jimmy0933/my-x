with open('app/src/main/java/com/example/xdownloader/ui/screens/DownloadsScreen.kt', 'r') as f:
    content = f.read()

import re
# We need to add cancel/retry buttons and display real state.
new_card = """@Composable
fun DownloadCard(download: DownloadEntity) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Url: ${download.url}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text("類型: ${download.type}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("狀態: ${download.status}", color = when(download.status) {
                "COMPLETED" -> MaterialTheme.colorScheme.primary
                "FAILED" -> MaterialTheme.colorScheme.error
                "CANCELLED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            })
            if (download.status == "DOWNLOADING" || download.status == "PENDING") {
                LinearProgressIndicator(progress = { download.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (download.status == "DOWNLOADING" || download.status == "PENDING") {
                    TextButton(onClick = {
                        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("dl_${download.url}")
                    }) {
                        Text("取消 (Cancel)")
                    }
                } else if (download.status == "FAILED" || download.status == "CANCELLED") {
                    TextButton(onClick = {
                        scope.launch {
                            db.downloadDao().updateProgress(download.url, "PENDING", 0)
                            val workManager = androidx.work.WorkManager.getInstance(context)
                            val data = androidx.work.Data.Builder()
                                .putString("url", download.url)
                                .putString("type", download.type)
                                .putString("title", download.title)
                                .build()

                            val request = androidx.work.OneTimeWorkRequestBuilder<com.example.xdownloader.worker.DownloadWorker>()
                                .setInputData(data)
                                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                                .build()

                            workManager.enqueueUniqueWork("dl_${download.url}", androidx.work.ExistingWorkPolicy.REPLACE, request)
                        }
                    }) {
                        Text("重試 (Retry)")
                    }
                }
            }
        }
    }
}"""
content = re.sub(r"@Composable\nfun DownloadCard\(download: DownloadEntity\) \{.*?\n\}\n(?!\n)", new_card + "\n", content, flags=re.DOTALL)
content = content.replace("import kotlinx.coroutines.flow.first", "import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.launch")
with open('app/src/main/java/com/example/xdownloader/ui/screens/DownloadsScreen.kt', 'w') as f:
    f.write(content)
