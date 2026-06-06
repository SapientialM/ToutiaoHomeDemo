package com.example.toutiao.presentation.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.ui.theme.RedMain
import timber.log.Timber

// =============================================================================
// VideoScreen — 视频频道页（对接Mock数据）
// =============================================================================
@Composable
fun VideoScreen(
    viewModel: VideoViewModel = hiltViewModel(),
) {
    var videos by remember { mutableStateOf(listOf<FeedCard.Video>()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasMore by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        loadVideos(viewModel, 0) { newVideos, more ->
            videos = newVideos
            hasMore = more
            isLoading = false
        }
    }

    Scaffold(
        topBar = { VideoTopBar() },
        containerColor = Color(0xFFF5F5F5),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(videos) { video ->
                VideoCardItem(video = video)
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = RedMain,
                        )
                    }
                }
            }

            if (hasMore && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable {
                                isLoading = true
                                currentPage++
                                loadVideos(viewModel, currentPage) { newVideos, more ->
                                    videos = videos + newVideos
                                    hasMore = more
                                    isLoading = false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "加载更多",
                            color = RedMain,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun loadVideos(
    viewModel: VideoViewModel,
    page: Int,
    onResult: (List<FeedCard.Video>, Boolean) -> Unit,
) {
    viewModel.loadVideos(page) { result, more ->
        onResult(result, more)
    }
}

@Composable
private fun VideoTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedMain)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "视频",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .clickable { }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun VideoCardItem(
    video: FeedCard.Video,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 视频封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = video.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color(0xFFF0F0F0)),
                error = ColorPainter(Color(0xFFE0E0E0)),
                modifier = Modifier.fillMaxSize(),
            )

            // 播放按钮
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "播放",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(48.dp),
                )
            }

            // 时长标签
            video.duration?.let { duration ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = duration,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 标题
        Text(
            text = video.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFF1A1A1A),
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(8.dp))

        // 作者和播放量
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = video.source,
                fontSize = 12.sp,
                color = Color(0xFF666666),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${video.commentCount}次播放",
                fontSize = 12.sp,
                color = Color(0xFF999999),
            )
        }
    }
}

// 保留 VideoItem 数据类用于预览
data class VideoItem(
    val id: String,
    val title: String,
    val author: String,
    val playCount: String,
    val duration: String,
    val imageUrl: String,
)

@Composable
fun VideoCard(
    video: VideoItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = video.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color(0xFFF0F0F0)),
                error = ColorPainter(Color(0xFFE0E0E0)),
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "播放",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(48.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = video.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFF1A1A1A),
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = video.author,
                fontSize = 12.sp,
                color = Color(0xFF666666),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${video.playCount}次播放",
                fontSize = 12.sp,
                color = Color(0xFF999999),
            )
        }
    }
}
