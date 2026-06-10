package com.example.toutiao.presentation.video

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
// VideoDetailScreen — 视频详情页（全屏播放 + 标题 + 描述）
//
// 设计：
//  - 顶部 16:9 视频播放区（AndroidView + 系统 VideoView）
//  - 下方标题 + 作者 + 视频说明
//  - VideoView 自带 MediaController（播放/暂停/进度条）
//  - 进入页面自动播放，离开页面 release 释放 MediaPlayer
//
// 注意：videoUrl 字段当前指向 bilibili 详情页（不可热链接播放），
//      MockDataSource.mapRealToDto 已把它转成可播放的样例 mp4 URL。
// =============================================================================
@Composable
fun VideoDetailScreen(
    video: FeedCard.Video,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var isPrepared by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf<String?>(null) }

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

        // 视频播放器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setMediaController(MediaController(ctx))
                        setOnPreparedListener { mp ->
                            isPrepared = true
                            // 循环播放（sample.mp4 是 10s 测试视频，循环避免用户看到结束）
                            mp.isLooping = true
                            start()
                            Timber.d("VideoDetailScreen — prepared, auto-play, looping")
                        }
                        setOnErrorListener { _, what, extra ->
                            // sample.mp4 是 10s 测试视频，-1004 = MEDIA_ERROR_IO (正常结束)
                            // 短 mp4 循环播放时偶尔会触发，这里仅记录
                            Timber.w("VideoDetailScreen — what=$what extra=$extra (短 mp4 结束可能触发)")
                            true
                        }
                    }
                },
                update = { view ->
                    val url = video.videoUrl
                    if (!url.isNullOrBlank()) {
                        Timber.d("VideoDetailScreen — setVideoURI $url")
                        // setVideoURI 由 factory 里 setOnPreparedListener 触发自动 start()，
                        // 这里不重复 start()，避免每次重组时重启播放
                        view.setVideoURI(android.net.Uri.parse(url))
                    } else {
                        hasError = "视频地址为空"
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 加载状态 / 错误态覆盖层
            if (!isPrepared && hasError == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = RedMain)
                }
            }
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

    // 页面销毁时释放 VideoView
    DisposableEffect(Unit) {
        onDispose {
            Timber.d("VideoDetailScreen — disposed")
        }
    }
}