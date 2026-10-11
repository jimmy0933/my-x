package com.example.xdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.xdownloader.browser.XWebView
import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.downloader.AndroidMediaDownloader
import com.example.xdownloader.ui.components.BrowserNavigationBar
import com.example.xdownloader.ui.components.MediaDownloadBottomSheet
import com.example.xdownloader.ui.theme.XDownloaderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            XDownloaderTheme {
                MainScreen()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember { AndroidMediaDownloader(context) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://x.com") }
    var loadingProgress by remember { mutableIntStateOf(0) }

    var detectedMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isBottomSheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (loadingProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        bottomBar = {
            BrowserNavigationBar(
                webView = webViewInstance,
                currentUrl = currentUrl,
                onHomeClick = {
                    currentUrl = "https://x.com"
                    webViewInstance?.loadUrl("https://x.com")
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = detectedMedia.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { isBottomSheetOpen = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Media"
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.detect_media) + " (${detectedMedia.size})"
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            XWebView(
                url = currentUrl,
                modifier = Modifier.fillMaxSize(),
                onWebViewCreated = { webViewInstance = it },
                onPageStarted = { url ->
                    currentUrl = url
                    detectedMedia = emptyList()
                },
                onPageFinished = { url ->
                    currentUrl = url
                },
                onProgressChanged = { progress ->
                    loadingProgress = progress
                },
                onMediaDetected = { items ->
                    detectedMedia = items
                }
            )

            if (isBottomSheetOpen) {
                MediaDownloadBottomSheet(
                    mediaItems = detectedMedia,
                    onDismiss = { isBottomSheetOpen = false },
                    onDownloadItem = { item ->
                        downloader.downloadWithDownloadManager(item)
                    },
                    onDownloadAll = { items ->
                        items.forEach { downloader.downloadWithDownloadManager(it) }
                        Toast.makeText(
                            context,
                            "已將 ${items.size} 個檔案加入下載佇列",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
}
