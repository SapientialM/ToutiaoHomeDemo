package com.example.toutiao.presentation.video

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.toutiao.presentation.common.LocalAppToastHost
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// VideoScreen — 底部「视频」Tab：抖音式沉浸式全屏翻页
//
// 设计依据：design/首页-视频.jpg
//   - VerticalPager 全屏翻页（上下滑动切换视频）
//   - 顶部 4 个 Tab（关注/精选/推荐/找短剧）+ 右上 + 号 / 耳机
//   - 右下垂直操作栏：头像(+ 关注) / 点赞 / 评论 / 收藏 / 分享
//   - 左下：@账号 + 描述 + 时间
//   - 黑底 + 封面铺满 + 沉浸式系统栏
//
// 数据流：复用 VideoViewModel.loadVideos(page, onResult)；
//         翻到末尾前 2 页自动触发 page+1 加载更多。
// =============================================================================

private val VIDEO_PAGER_TABS = listOf("关注", "精选", "推荐", "找短剧")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoScreen(
    viewModel: VideoViewModel = hiltViewModel(),
    onVideoClick: (FeedCard.Video) -> Unit = {},
) {
    var videos by remember { mutableStateOf(listOf<FeedCard.Video>()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasMore by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(0) }
    var currentTab by remember { mutableIntStateOf(1) } // 默认「精选」

    // 初次加载
    LaunchedEffect(Unit) {
        loadVideos(viewModel, 0) { newVideos, more ->
            videos = newVideos
            hasMore = more
            isLoading = false
        }
    }

    // 进入即沉浸式，退出恢复
    HideSystemBarsOnEnter()

    val pagerState = rememberPagerState(pageCount = { videos.size })

    // 翻到末尾前 2 页自动加载更多
    val nearEnd by remember {
        derivedStateOf {
            videos.isNotEmpty() && pagerState.currentPage >= videos.size - 2
        }
    }
    LaunchedEffect(nearEnd, hasMore, isLoading) {
        if (nearEnd && hasMore && !isLoading) {
            isLoading = true
            val nextPage = currentPage + 1
            currentPage = nextPage
            loadVideos(viewModel, nextPage) { newVideos, more ->
                videos = videos + newVideos
                hasMore = more
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (videos.isEmpty() && isLoading) {
            // 首次加载态
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        } else if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无视频", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> videos[index].id },
                pageContent = { index ->
                    VideoPagerPage(
                        video = videos[index],
                        isCurrentPage = pagerState.currentPage == index,
                        onCommentClick = { onVideoClick(videos[index]) },
                    )
                },
            )
        }

        // 顶部 Tab 行（覆盖在 Pager 之上）
        VideoPagerTopBar(
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun VideoPagerTopBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 顶部不做 statusBarsPadding：沉浸式场景下系统栏已隐藏,
    // Tab 直接顶到屏幕最上方
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
                ),
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
        ) {
            VIDEO_PAGER_TABS.forEachIndexed { index, label ->
                val selected = index == currentTab
                Text(
                    text = label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                    fontSize = if (selected) 17.sp else 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        IconButton(onClick = { /* 占位：发视频入口 */ }) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "发视频",
                tint = Color.White,
            )
        }
        IconButton(onClick = { /* 占位：耳机(音频直播) */ }) {
            Icon(
                imageVector = Icons.Filled.Headphones,
                contentDescription = "音频",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun VideoPagerPage(
    video: FeedCard.Video,
    isCurrentPage: Boolean,
    onCommentClick: () -> Unit,
) {
    val toast = LocalAppToastHost.current
    val bvid = remember(video.videoUrl) { extractBvid(video.videoUrl) }
    val useWebView = bvid != null
    var isPlaying by remember { mutableStateOf(false) }

    // 离开当前页时强制停止播放,避免 WebView 在后台继续出声
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) isPlaying = false
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 封面铺满屏幕（始终渲染：未播放时主视觉 / 播放时 WebView 加载前的占位）
        AsyncImage(
            model = video.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color(0xFF1A1A1A)),
            error = ColorPainter(Color(0xFF1A1A1A)),
            modifier = Modifier.fillMaxSize(),
        )

        if (isPlaying) {
            // ── 就地播放：在 Pager 内直接渲染播放器,不再跳详情页 ──
            //
            // 注意:不要在这里套一层 fillMaxSize().clickable { isPlaying = false } 覆盖层,
            // 否则会拦截玩家(WebView / VideoView)自身的点击事件,
            // 导致 B 站播放按钮 / 进度条无法响应。
            // 关闭播放的入口有两个:× 按钮 / 滑到下一页。
            Box(modifier = Modifier.fillMaxSize()) {
                if (useWebView) {
                    BilibiliWebPlayer(
                        bvid = bvid!!,
                        page = 1,
                        highQuality = 1,
                    )
                } else {
                    FallbackVideoPlayer(
                        videoUrl = video.videoUrl,
                        onError = { msg ->
                            toast.showError("视频暂时无法播放，请检查网络或稍后再试")
                        },
                    )
                }

                // 关闭按钮(放在 TopStart,避免与 TopBar 的 +/耳机按钮在 TopEnd 重叠)
                IconButton(
                    onClick = { isPlaying = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭播放",
                        tint = Color.White,
                    )
                }
            }
        } else {
            // ── 未播放：中央大播放按钮（点击 → 在 Pager 内开始播放,不再跳详情页） ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isPlaying = true },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        // 底部黑色渐变，提升文字可读性
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                )
                .height(280.dp),
        )

        // 右下垂直操作栏
        VideoPagerActionBar(
            video = video,
            onCommentClick = onCommentClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp, bottom = 100.dp),
        )

        // 左下账号 + 描述 + 时间
        VideoPagerInfoOverlay(
            video = video,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun VideoPagerActionBar(
    video: FeedCard.Video,
    onCommentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toast = LocalAppToastHost.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 头像 + 关注小红点
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0)),
            )
            Box(
                modifier = Modifier
                    .offset(y = 6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(RedMain)
                    .clickable { toast.showInfo("关注功能尚未接入") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "关注",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        ActionItem(
            icon = Icons.Filled.FavoriteBorder,
            count = formatCount(video.commentCount),
            onClick = { toast.showInfo("点赞功能尚未接入") },
        )
        ActionItem(
            icon = Icons.Filled.ChatBubbleOutline,
            count = formatCount((video.commentCount / 3).coerceAtLeast(0)),
            onClick = onCommentClick,
        )
        ActionItem(
            icon = Icons.Filled.StarBorder,
            count = formatCount((video.commentCount / 5).coerceAtLeast(0)),
            onClick = { toast.showInfo("收藏功能尚未接入") },
        )
        ActionItem(
            icon = Icons.Filled.Share,
            count = "分享",
            onClick = { toast.showInfo("分享功能尚未接入") },
        )
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = count,
            color = Color.White,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun VideoPagerInfoOverlay(
    video: FeedCard.Video,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "@${video.source}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "关注",
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = video.title,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = video.publishTime,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
        )
    }
}

private fun formatCount(n: Int): String = when {
    n >= 10_000 -> "%.1f万".format(n / 10_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    n > 0 -> n.toString()
    else -> "0"
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

// =============================================================================
// 以下两个 API 保留：被 presentation/common/FeedCardItem.kt 引用，
// 改了会导致首页 Feed 视频卡片渲染失败。
// =============================================================================

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
                .height(180.dp)
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
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
                Text(text = video.duration, color = Color.White, fontSize = 11.sp)
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
            Text(text = video.author, fontSize = 12.sp, color = Color(0xFF666666))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${video.playCount}次播放",
                fontSize = 12.sp,
                color = Color(0xFF999999),
            )
        }
    }
}
