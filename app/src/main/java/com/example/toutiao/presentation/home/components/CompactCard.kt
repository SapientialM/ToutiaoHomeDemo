package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.domain.model.FeedCard

/**
 * MVPTask #3: 紧凑无图卡片
 *
 * - 单行/双行标题 (16sp Medium)
 * - 灰色信息行 (来源 · 时间 · 评论数)
 * - 行高更紧凑（高度 ≈ 70dp）
 */
@Composable
fun CompactCard(
    card: FeedCard.Compact,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = card.title,
            color = Color(0xFF1A1A1A),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(4.dp))
        BottomInfoRow(
            source = card.source,
            publishTime = card.publishTime,
            commentCount = card.commentCount,
        )
    }
}