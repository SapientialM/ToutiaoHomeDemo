package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.toutiao.ui.theme.TextPrimary
import com.example.toutiao.ui.theme.TextSecondary

/**
 * MVPTask #3: 资讯速递卡片
 *
 * 设计稿：
 *  ┌────────────────────────────────────────────┐
 *  │  💧 高考资讯速递                            │
 *  ├────────────────────────────────────────────┤
 *  │ 速递！2026 高考作文题来了，专家在线深度拆解  │
 *  │                                            │
 *  │   [————— 大图 —————]                       │
 *  │                                            │
 *  │ 1小时前                              ⋯     │
 *  └────────────────────────────────────────────┘
 *
 * 用 蓝→白 渐变背景 + 蓝色水滴 icon + 标题
 */
@Composable
fun RecommendFlashCard(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEFF7FF),  // 浅蓝
                        Color(0xFFFAFAFA),  // 浅灰
                    ),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        // 头部：💧 高考资讯速递
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 蓝色水滴占位
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF4A9DFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text("💧", fontSize = 12.sp)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "高考资讯速递",
                color = Color(0xFF1B6BD8),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        // 主体：标题 + 缩略图
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "速递！2026 高考作文题来了，专家在线深度拆解",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            // 缩略图占位
            Box(
                modifier = Modifier
                    .size(108.dp, 72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFE53935),
                                Color(0xFFFFB300),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("🏮", fontSize = 24.sp)
            }
        }
        // 底部信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "1小时前",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "⋯",
                color = TextSecondary,
                fontSize = 18.sp,
            )
        }
        // 分隔
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFFF5F5F5)),
        )
    }
}
