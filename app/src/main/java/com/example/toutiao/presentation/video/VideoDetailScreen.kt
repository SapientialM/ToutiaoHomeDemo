package com.example.toutiao.presentation.video

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.ui.theme.RedMain
import timber.log.Timber

// =============================================================================
// VideoDetailScreen — 视频详情页
//
// 播放器选型：
//  - url 包含 bilibili.com/video/BVxxx → WebView 加载
//    https://player.bilibili.com/player.html?bvid=BVxxx&page=1&high_quality=1
//    （官方 HTML5 播放器，跨平台支持好）
//  - 其他 URL → 兜底用 AndroidView + 系统 VideoView
//
// 视频信息：标题 + 作者 + 播放次数 + 发布时间
// =============================================================================
@Composable
fun VideoDetailScreen(
    video: FeedCard.Video,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val bvid = remember(video.videoUrl) { extractBvid(video.videoUrl) }
    val useWebView = bvid != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        // 顶部栏：返回 + 视频标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color(0xFF1A1A1A),
                )
            }
            Text(
                text = "视频",
                color = Color(0xFF1A1A1A),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // 视频播放器：16:9 黑色背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            if (useWebView) {
                BilibiliWebPlayer(
                    bvid = bvid!!,
                    page = 1,
                    highQuality = 1,
                )
            } else {
                FallbackVideoPlayer(
                    videoUrl = video.videoUrl,
                    onError = { /* ignore, log in fallback */ },
                )
            }
        }

        // 视频信息
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Text(
                    text = video.title,
                    color = Color(0xFF1A1A1A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.source,
                        color = Color(0xFF666666),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.size(width = 12.dp, height = 0.dp))
                    Text(
                        text = "${video.commentCount}次播放",
                        color = Color(0xFF999999),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = video.publishTime,
                    color = Color(0xFF999999),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Bilibili Web Player — 用 WebView 加载官方 HTML5 播放器
//
// URL: https://player.bilibili.com/player.html?bvid=BVxxx&page=1&high_quality=1
// 高 quality=1 优先清晰度高码率
// -----------------------------------------------------------------------------
@Composable
private fun BilibiliWebPlayer(
    bvid: String,
    page: Int,
    highQuality: Int,
) {
    var isLoading by remember { mutableStateOf(true) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // B 站播放器页面是 HTTPS，但内部可能用 mixed content
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        isLoading = true
                        Timber.d("BilibiliWebPlayer — pageStarted: $url")
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        isLoading = false
                        Timber.d("BilibiliWebPlayer — pageFinished: $url")
                    }
                }
                val playerUrl = "https://player.bilibili.com/player.html?bvid=$bvid&page=$page&high_quality=$highQuality"
                Timber.d("BilibiliWebPlayer — loadUrl: $playerUrl")
                loadUrl(playerUrl)
            }
        },
        update = { /* 不重复 load */ },
        modifier = Modifier.fillMaxSize(),
    )

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = RedMain)
        }
    }
}

// -----------------------------------------------------------------------------
// Fallback VideoView 兜底
// -----------------------------------------------------------------------------
@Composable
private fun FallbackVideoPlayer(
    videoUrl: String?,
    onError: () -> Unit,
) {
    var hasError by remember { mutableStateOf<String?>(null) }

    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setMediaController(MediaController(ctx))
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                    Timber.d("FallbackVideoPlayer — prepared, auto-play, looping")
                }
                setOnErrorListener { _, what, extra ->
                    Timber.w("FallbackVideoPlayer — what=$what extra=$extra")
                    hasError = "播放失败 (code=$what)"
                    true
                }
            }
        },
        update = { view ->
            if (!videoUrl.isNullOrBlank()) {
                Timber.d("FallbackVideoPlayer — setVideoURI $videoUrl")
                view.setVideoURI(android.net.Uri.parse(videoUrl))
            } else {
                hasError = "视频地址为空"
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    hasError?.let { err ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = err,
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 提取 B 站 BV 号
// 支持 url 形式：
//  - https://www.bilibili.com/video/BV1xx411c7mD
//  - https://www.bilibili.com/video/BV1xx411c7mD?p=1
//  - https://m.bilibili.com/video/BV1xx
//  - 已是 BV1xx 字符串
// -----------------------------------------------------------------------------
private fun extractBvid(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val regex = Regex("""BV([0-9A-Za-z]{8,})""")
    return regex.find(url)?.value
}