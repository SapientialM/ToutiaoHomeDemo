package com.example.toutiao.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 首页悬浮提示卡
 *
 * 设计稿（首页-推荐-顶部 / 首页-热榜）：
 *  - 位置：右下角浮于内容之上
 *  - 样式：白底圆角 + 阴影 + 左侧缩略图 40x40 + 中部标题副标题 + 右上 X
 *  - 标题示例："高考作文题来了" / 副标题："去热榜看详情 >"
 *
 * 当前实现带可关闭状态（用户点 X 后续不再展示，本次会话内）。
 */
@Composable
fun FloatingHintCard(
    title: String,
    subtitle: String,
    thumbnailTint: Color = Color(0xFFFFE8E5),
    thumbnailText: String = "📰",
    onClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(thumbnailTint),
                contentAlignment = Alignment.Center,
            ) {
                Text(thumbnailText, fontSize = 18.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = Color(0xFF1A1A1A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF4A90E2),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * 首页悬浮提示卡 - 状态容器（带可见性管理）
 */
@Composable
fun FloatingHintCardWithState(
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }
    if (visible) {
        FloatingHintCard(
            title = title,
            subtitle = subtitle,
            onClick = onClick,
            onDismiss = { visible = false },
            modifier = modifier,
        )
    }
}

/**
 * 一键回顶部按钮
 *
 * 设计参考今日头条/抖音：右下角圆形 FAB，白底 + 阴影 + 向上箭头。
 * 使用 AnimatedVisibility 做淡入淡出 + 缩放，避免在顶部时突兀地占着位置。
 *
 * 显示时机由调用方控制（一般：firstVisibleItemIndex > 3），本组件只管渲染。
 */
@Composable
fun BackToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "回顶部",
                tint = Color(0xFF1A1A1A),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
