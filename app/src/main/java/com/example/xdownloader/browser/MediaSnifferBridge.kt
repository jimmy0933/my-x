package com.example.xdownloader.browser

import android.content.Context
import android.webkit.JavascriptInterface
import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import com.example.xdownloader.utils.MediaSnifferUtils
import org.json.JSONArray
import org.json.JSONObject

class MediaSnifferBridge(
    private val onMediaDetected: (List<MediaItem>) -> Unit
) {

    @JavascriptInterface
    fun processPageMedia(jsonString: String) {
        try {
            val items = mutableListOf<MediaItem>()
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val typeStr = obj.optString("type", "IMAGE")
                val rawUrl = obj.optString("url", "")
                val posterUrl = obj.optString("poster", "")

                if (rawUrl.isNotBlank()) {
                    if (typeStr == "IMAGE" && MediaSnifferUtils.isContentImage(rawUrl)) {
                        val highResUrl = MediaSnifferUtils.sanitizeImageUrl(rawUrl)
                        items.add(
                            MediaItem(
                                id = highResUrl,
                                url = highResUrl,
                                thumbnailUrl = highResUrl,
                                type = MediaType.IMAGE,
                                quality = "原圖 (HQ)"
                            )
                        )
                    } else if (typeStr == "VIDEO" && MediaSnifferUtils.isVideoUrl(rawUrl)) {
                        items.add(
                            MediaItem(
                                id = rawUrl,
                                url = rawUrl,
                                thumbnailUrl = posterUrl.ifEmpty { rawUrl },
                                type = MediaType.VIDEO,
                                quality = "HD 影片"
                            )
                        )
                    }
                }
            }

            // Distinct by URL
            val distinctItems = items.distinctBy { it.url }
            onMediaDetected(distinctItems)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        val JS_SNIFFER_SCRIPT = """
            (function() {
                var mediaList = [];

                // 1. Scan images
                var imgs = document.getElementsByTagName('img');
                for (var i = 0; i < imgs.length; i++) {
                    var src = imgs[i].src || imgs[i].getAttribute('src');
                    if (src && src.indexOf('pbs.twimg.com/media/') !== -1) {
                        mediaList.push({
                            type: 'IMAGE',
                            url: src,
                            poster: src
                        });
                    }
                }

                // 2. Scan videos & video tags
                var videos = document.getElementsByTagName('video');
                for (var j = 0; j < videos.length; j++) {
                    var v = videos[j];
                    var poster = v.poster || '';
                    var vSrc = v.src;
                    if (!vSrc && v.children) {
                        for (var k = 0; k < v.children.length; k++) {
                            if (v.children[k].tagName === 'SOURCE') {
                                vSrc = v.children[k].src;
                                break;
                            }
                        }
                    }
                    if (vSrc) {
                        mediaList.push({
                            type: 'VIDEO',
                            url: vSrc,
                            poster: poster
                        });
                    }
                }

                // 3. Scan performance entries for twimg video API responses
                if (window.performance && window.performance.getEntriesByType) {
                    var resources = window.performance.getEntriesByType('resource');
                    for (var r = 0; r < resources.length; r++) {
                        var resUrl = resources[r].name;
                        if (resUrl && resUrl.indexOf('video.twimg.com') !== -1) {
                            mediaList.push({
                                type: 'VIDEO',
                                url: resUrl,
                                poster: ''
                            });
                        }
                    }
                }

                XDownloaderBridge.processPageMedia(JSON.stringify(mediaList));
            })();
        """.trimIndent()
    }
}
