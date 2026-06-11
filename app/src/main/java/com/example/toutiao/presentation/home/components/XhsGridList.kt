package com.example.toutiao.presentation.home.components

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.example.toutiao.domain.model.FeedCard

/**
 * MVPTask #8: 小红书风格双列卡片网格
 *
 * 设计稿：
 *  - 2 列等宽
 *  - 每张卡：上图（按比例 3:4） + 标题（2 行截断） + 头像 + 名字 + ❤️
 *  - 无限下拉
 */
data class XhsCard(
    val id: String,
    val title: String,
    val authorName: String,
    val authorAvatar: String,
    val likes: String,
    val coverUrl: String? = null,
    val coverEmoji: String,
    val coverColor1: Color,
    val coverColor2: Color,
)

@Composable
fun XhsGridList(
    items: List<XhsCard>,
    modifier: Modifier = Modifier,
    onItemClick: (XhsCard) -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize().background(Color(0xFFFAFAFA)),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { card ->
            XhsCardItem(card = card, onClick = { onItemClick(card) })
        }
    }
}

@Composable
private fun XhsCardItem(card: XhsCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
    ) {
        // 封面（3:4 比例）
        // 优先用真实 coverUrl（来自 v2 数据源），没有时降级到渐变 + emoji 占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .background(
                    Brush.linearGradient(
                        colors = listOf(card.coverColor1, card.coverColor2),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!card.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = card.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(Color(0xFFF0F0F0)),
                    error = ColorPainter(Color(0xFFE0E0E0)),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(card.coverEmoji, fontSize = 56.sp)
            }
        }
        // 标题
        Text(
            text = card.title,
            color = Color(0xFF1A1A1A),
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            lineHeight = 18.sp,
        )
        // 作者 + 点赞
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFB6C1)),
                contentAlignment = Alignment.Center,
            ) {
                Text(card.authorAvatar, fontSize = 9.sp)
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = card.authorName,
                color = Color(0xFF999999),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "❤ ${card.likes}",
                color = Color(0xFF999999),
                fontSize = 10.sp,
            )
        }
    }
}

/**
 * 把 Mock FeedCard 转成 XhsCard 用于发现页。
 * coverUrl 透传原始 imageUrl（来自 v2 真实数据）；空时降级到渐变 + emoji 占位。
 */
fun feedCardToXhsCard(card: FeedCard): XhsCard {
    val title = card.title
    val emojis = listOf("🌸", "🍰", "📸", "☕", "🛍️", "✈️", "🎨", "📚")
    val colors = listOf(
        Color(0xFFFFB6C1) to Color(0xFFFF8FA3),
        Color(0xFFFFE4B5) to Color(0xFFFFA07A),
        Color(0xFFB0E0E6) to Color(0xFF87CEEB),
        Color(0xFFDDA0DD) to Color(0xFFEE82EE),
        Color(0xFF98FB98) to Color(0xFF90EE90),
        Color(0xFFFFD700) to Color(0xFFFFA500),
    )
    val hash = title.hashCode()
    val absHash = if (hash < 0) -hash else hash
    // FeedCard 是 sealed class，imageUrl 分布在 3 个子类型（LeftTextRightImage / LargeImage / Video）
    // 用 when 提取；TextTop/Compact 无图，保持 null 走降级路径
    val resolvedCoverUrl: String? = when (card) {
        is FeedCard.LeftTextRightImage -> card.imageUrl.takeIf { it.isNotBlank() }
        is FeedCard.LargeImage -> card.imageUrl.takeIf { it.isNotBlank() }
        is FeedCard.Video -> card.imageUrl.takeIf { it.isNotBlank() }
        is FeedCard.TextTop -> null
        is FeedCard.Compact -> null
    }
    return XhsCard(
        id = card.id,
        title = title,
        authorName = card.source,
        authorAvatar = card.source.firstOrNull()?.toString() ?: "?",
        likes = (100 + (absHash % 9000)).toString(),
        coverUrl = resolvedCoverUrl,
        coverEmoji = emojis[absHash % emojis.size],
        coverColor1 = colors[absHash % colors.size].first,
        coverColor2 = colors[absHash % colors.size].second,
    )
}
