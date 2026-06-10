package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import com.example.toutiao.domain.model.HotBadge
import com.example.toutiao.domain.model.HotListItem
import com.example.toutiao.domain.model.HotQuickAction
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// HotListView — 热榜频道专用布局
//
// 设计要点（参考 design/首页-热榜.jpg）：
//  1. 顶部 4 个圆角胶囊快捷入口（横向滚动）
//  2. 列表项：序号(15sp Bold) + 标题(15sp Regular) + 标签徽标
//     - 🔥 火焰：橙色实心图标
//     - 爆：红底白字圆角
//     - 热：红底白字小方块
//     - 新：红底白字小方块
//     - 辟谣：蓝底白字方块
//  3. 列表项之间 1px 浅灰分割线 (#F0F0F0)
// =============================================================================

/** 红底/蓝底徽标的统一容器（爆/热/新/辟谣） */
@Composable
private fun HotBadgeChip(text: String, bg: Color, fontSize: Int = 10) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HotBadgeFor(item: HotListItem) {
    when (item.badge) {
        HotBadge.Fire -> {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = "热",
                tint = Color(0xFFFF8533),
                modifier = Modifier.size(16.dp),
            )
        }
        HotBadge.Boom -> HotBadgeChip("爆", RedMain, fontSize = 11)
        HotBadge.Hot -> HotBadgeChip("热", RedMain, fontSize = 10)
        HotBadge.New -> HotBadgeChip("新", RedMain, fontSize = 10)
        HotBadge.Rumor -> HotBadgeChip("辟谣", Color(0xFF4A90E2), fontSize = 10)
        HotBadge.None -> Unit
    }
}

@Composable
fun HotQuickActionRow(
    actions: List<HotQuickAction>,
    modifier: Modifier = Modifier,
    onActionClick: (HotQuickAction) -> Unit = {},
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(RedMain)
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(actions, key = { it.id }) { action ->
            HotQuickActionPill(action, onClick = { onActionClick(action) })
        }
    }
}

@Composable
private fun HotQuickActionPill(action: HotQuickAction, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = action.icon,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = action.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = action.subtitle,
                color = Color(0xFFFFE5E5),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun HotListView(
    items: List<HotListItem>,
    quickActions: List<HotQuickAction>,
    onItemClick: (HotListItem) -> Unit,
    modifier: Modifier = Modifier,
    onQuickActionClick: (HotQuickAction) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        item(key = "hot_quick_actions") {
            HotQuickActionRow(actions = quickActions, onActionClick = onQuickActionClick)
        }
        item(key = "hot_divider") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0xFFF5F5F5)),
            )
        }
        items(items, key = { it.id }) { item ->
            HotListItemRow(item = item, onClick = { onItemClick(item) })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFF0F0F0)),
            )
        }
    }
}

@Composable
private fun HotListItemRow(item: HotListItem, onClick: () -> Unit) {
    // MVPTask #4: 每行浅色渐变红背景（白→极浅红，~8% 红 alpha，不深但可辨）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFEBEB),  // 浅红（白 92% + 红 8%）
                        Color(0xFFFFFAFA),  // 微红
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),  // 紧凑：14→10
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 序号：1=深红/2=橙/3=黄/4+=灰（行业惯例视觉层级）
        val rankColor = when (item.rank) {
            1 -> Color(0xFFFF3B30)
            2 -> Color(0xFFFFA940)
            3 -> Color(0xFFFAAD14)
            else -> Color(0xFF999999)
        }
        Text(
            text = item.rank.toString(),
            color = rankColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.title,
            color = Color(0xFF1A1A1A),
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.badge != HotBadge.None) {
            Spacer(Modifier.width(8.dp))
            HotBadgeFor(item)
        }
    }
}
