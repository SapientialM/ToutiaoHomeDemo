package com.example.toutiao.presentation.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import com.example.toutiao.data.remote.datasource.CommentDataSource
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.presentation.comment.CommentSection
import com.example.toutiao.presentation.common.LocalAppToastHost
import com.example.toutiao.ui.theme.RedMain
import timber.log.Timber

// =============================================================================
// VideoDetailScreen — 视频详情页
//
// 播放器选型：
//  - url 含 bilibili.com/video/BVxxx → WebView 加载 player.bilibili.com
//  - 其他 URL → 兜底用 AndroidView + 系统 VideoView
//
// 全屏切换：
//  - isFullScreen = true → 玩家 fillMaxSize，黑色背景，隐去 status/nav bar
//  - 点击播放器区域或右上角"退出全屏"按钮 → 恢复正常
// =============================================================================
@Composable
fun VideoDetailScreen(
    video: FeedCard.Video,
    onBack: () -> Unit,
    commentDataSource: CommentDataSource? = null,
) {
    val context = LocalContext.current
    val toast = LocalAppToastHost.current
    val bvid = remember(video.videoUrl) { extractBvid(video.videoUrl) }
    val useWebView = bvid != null
    var isFullScreen by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }

    // 错误时 toast 提示（ToC 化文案）
    LaunchedEffect(videoError) {
        videoError?.let {
            toast.showError("视频暂时无法播放，请检查网络或稍后再试")
            videoError = null
        }
    }

    // 全屏：隐藏/显示 system bars
    val systemController = rememberSystemBarsController()
    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            systemController.hide()
        } else {
            systemController.show()
        }
    }

    if (isFullScreen) {
        // ── 全屏模式：玩家填满屏幕 ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { isFullScreen = false },
            contentAlignment = Alignment.Center,
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
                    onError = { msg -> videoError = msg },
                )
            }
            // 退出全屏浮按钮
            IconButton(
                onClick = { isFullScreen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.FullscreenExit,
                    contentDescription = "退出全屏",
                    tint = Color.White,
                )
            }
        }
    } else {
        // ── 默认模式：16:9 玩家 + 顶部栏 + 信息 + 评论 ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            // 顶部栏：返回 + 视频标题 + 全屏按钮
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
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconButton(onClick = { isFullScreen = true }) {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = "全屏",
                        tint = Color(0xFF1A1A1A),
                    )
                }
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
                        onError = { msg -> videoError = msg },
                    )
                }
            }

            // 视频信息 + 评论
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentPadding = PaddingValues(top = 16.dp, bottom = 0.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFEEEEEE)),
                    )
                }
                // 评论区块
                if (commentDataSource != null) {
                    item {
                        CommentSection(
                            newsId = video.sourceUrl ?: video.id,
                            commentDataSource = commentDataSource,
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            systemController.show()
        }
    }
}

// -----------------------------------------------------------------------------
// 隐藏/显示 system bars 的工具（沉浸式）
// -----------------------------------------------------------------------------
@Composable
private fun rememberSystemBarsController(): SystemBarsController {
    val context = LocalContext.current
    val controller = remember { SystemBarsController(context) }
    return controller
}

private class SystemBarsController(private val context: Context) {
    fun hide() {
        val activity = context.findActivity() ?: return
        val window = activity.window
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    fun show() {
        val activity = context.findActivity() ?: return
        val window = activity.window
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// -----------------------------------------------------------------------------
// Bilibili Web Player
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
        update = { },
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
// Fallback VideoView — 视频错误时 toast
// -----------------------------------------------------------------------------
@Composable
private fun FallbackVideoPlayer(
    videoUrl: String?,
    onError: (String) -> Unit,
) {
    var hasError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(hasError) {
        hasError?.let {
            // 视频错误 toast
            onError("视频播放失败：$it（$videoUrl）")
        }
    }

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
                    val msg = "code=$what extra=$extra"
                    Timber.w("FallbackVideoPlayer — $msg")
                    hasError = msg
                    true
                }
            }
        },
        update = { view ->
            if (!videoUrl.isNullOrBlank()) {
                Timber.d("FallbackVideoPlayer — setVideoURI $videoUrl")
                view.setVideoURI(android.net.Uri.parse(videoUrl))
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun extractBvid(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val regex = Regex("""BV([0-9A-Za-z]{8,})""")
    return regex.find(url)?.value
}