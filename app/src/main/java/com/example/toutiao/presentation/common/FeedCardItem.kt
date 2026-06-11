package com.example.toutiao.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.presentation.home.components.CompactCard
import com.example.toutiao.presentation.home.components.LargeImageCard
import com.example.toutiao.presentation.home.components.LeftTextRightImageCard
import com.example.toutiao.presentation.home.components.TextTopCard
import com.example.toutiao.presentation.video.VideoCard
import com.example.toutiao.presentation.video.VideoItem
import com.example.toutiao.ui.theme.RedMain

/**
 * 通用 FeedCard 渲染组件
 * 根据 FeedCard 类型自动分发到对应卡片组件
 */
@Composable
fun FeedCardItem(
    card: FeedCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        when (card) {
            is FeedCard.TextTop -> TextTopCard(card)
            is FeedCard.LeftTextRightImage -> LeftTextRightImageCard(card)
            is FeedCard.LargeImage -> LargeImageCard(card)
            is FeedCard.Video -> VideoCardItem(card)
            is FeedCard.Compact -> CompactCard(card)
        }
    }
}

/**
 * 视频卡片组件
 */
@Composable
fun VideoCardItem(
    card: FeedCard.Video,
    modifier: Modifier = Modifier,
) {
    // 复用 LargeImageCard 的样式，但添加视频标识
    // PM ISSUE-001 修复：playCount 之前在 FeedCardItem 和 VideoCard 双重拼接 "${x}次播放" + "次播放"，
    // 渲染出 "517次播放次播放"。这里只传数字 517，由 VideoScreen 统一加 "次播放" 后缀。
    VideoCard(
        video = VideoItem(
            id = card.id,
            title = card.title,
            author = card.source,
            playCount = "${card.commentCount}",
            duration = card.duration ?: "00:00",
            imageUrl = card.imageUrl,
        ),
        modifier = modifier,
    )
}

/**
 * 加载指示器组件
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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
