package com.example.toutiao.presentation.home.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.RedMain

/**
 * MVPTask #6: 深圳频道视频轮播
 *
 * 设计稿：在天气条下方放一个横向滚动的视频轮播（3 张视频卡）
 * 每张卡：大图 + ▶ 播放图标 + 时长 + 标题
 */
@Composable
fun ShenzhenVideoCarousel(
    modifier: Modifier = Modifier,
    onVideoClick: (String) -> Unit = {},
) {
    val videos = listOf(
        VideoCarouselItem("v1", "深圳湾公园春日花海实拍", "02:18", "🌅"),
        VideoCarouselItem("v2", "华强北最新电子产品评测", "05:42", "📱"),
        VideoCarouselItem("v3", "深南大道夜景延时摄影", "01:30", "🌃"),
        VideoCarouselItem("v4", "深圳地铁 16 号线试乘体验", "03:15", "🚇"),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "📹 深圳视频",
                color = Color(0xFF1A1A1A),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "更多 ›",
                color = Color(0xFF999999),
                fontSize = 12.sp,
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(videos, key = { it.id }) { video ->
                VideoCarouselCard(video = video, onClick = { onVideoClick(video.id) })
            }
        }
    }
}

private data class VideoCarouselItem(
    val id: String,
    val title: String,
    val duration: String,
    val emoji: String,
)

@Composable
private fun VideoCarouselCard(video: VideoCarouselItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
    ) {
        // 缩略图
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1B6BD8),
                            Color(0xFF4A9DFF),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(video.emoji, fontSize = 36.sp)
            // ▶ 播放图标
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            // 时长徽标
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    fontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = video.title,
            color = Color(0xFF1A1A1A),
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
