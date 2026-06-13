package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.RedMain

/**
 * 「上次看到这里」提示条
 *
 * 渲染规则（由调用方决定是否显示）：
 *  - 当前列表中存在 id == lastSeenCardId 的项
 *  - 用户尚未滚过该位置（firstVisibleItemIndex < lastSeenIndex）
 *  - 当前 tab 持久化的 lastSeenCardId 不为 null
 *
 * @param relativeMinutes 距离上次阅读的分钟数（用于文案）
 * @param isResolving 数据尚未加载到位（lastSeenCardId 不在当前 snapshot）,
 *                   文案改为"正在加载原位置..."提示用户系统正在工作
 * @param onClick 用户点击提示
 */
@Composable
fun LastSeenHint(
    relativeMinutes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isResolving: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFE8E5)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = RedMain,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "上次看到这里",
            color = Color(0xFF666666),
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .height(1.dp)
                .width(24.dp)
                .background(Color(0xFFCCCCCC)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = relativeTimeText(relativeMinutes),
            color = RedMain,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (isResolving) "正在加载原位置..." else "点击回到原位置",
            color = Color(0xFF999999),
            fontSize = 11.sp,
        )
    }
}

private fun relativeTimeText(minutes: Long): String = when {
    minutes < 1L -> "刚刚看过"
    minutes < 60L -> "${minutes} 分钟前看过"
    minutes < 60L * 24 -> "${minutes / 60} 小时前看过"
    else -> "${minutes / (60L * 24)} 天前看过"
}
