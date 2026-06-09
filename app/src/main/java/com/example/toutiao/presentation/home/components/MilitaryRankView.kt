package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.RedMain

/**
 * 军事频道 - 榜单头
 *
 * 设计稿：白底，左盾牌 icon + "军事榜" 15sp Bold，右侧"完整榜单 >" 12sp 灰
 */
@Composable
fun MilitaryRankHeader(
    title: String = "军事榜",
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFE8E5)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = RedMain,
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            color = Color(0xFF1A1A1A),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onMoreClick),
        ) {
            Text(
                text = "完整榜单",
                color = Color(0xFF999999),
                fontSize = 12.sp,
            )
            Text(
                text = " ›",
                color = Color(0xFF999999),
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * 军事频道 - 榜单列表项
 *
 * 设计稿：白底，左红圆点 4dp + 标题 15sp Regular；部分末尾有红色"新"角标
 */
@Composable
fun MilitaryRankItem(
    title: String,
    isNew: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(RedMain),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = Color(0xFF1A1A1A),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isNew) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFF26340))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "新",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 军事频道 - 项间分割线（缩进 31dp，避开左侧红圆点）
 */
@Composable
fun MilitaryRankDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 31.dp)
            .height(1.dp)
            .background(Color(0xFFF0F0F0)),
    )
}
