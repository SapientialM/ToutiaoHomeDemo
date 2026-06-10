package com.example.toutiao.presentation.hot

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.toutiao.domain.model.HotBadge
import com.example.toutiao.domain.model.HotListItem
import com.example.toutiao.domain.model.HotQuickAction

// =============================================================================
// HotTopicScreen — 热榜快捷入口的子分页
//
// 设计：进入"子榜单"展示该主题下的关联条目。当前为占位实现，主题相关
// 条目由 HomeUiState.hotListItems 中关键词匹配的子集填充；后续可换成 API。
//
// 头部：返回 + 主题名（深红大字）
// 列表：与 HotListView 同样的"行条目 + 浅红渐变行底"样式
// =============================================================================
@Composable
fun HotTopicScreen(
    action: HotQuickAction,
    items: List<HotListItem>,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color(0xFF1A1A1A),
                )
            }
            Text(
                text = "热榜子分页",
                color = Color(0xFF1A1A1A),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // 主题头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF1F0))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFE0DC)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = action.icon,
                    fontSize = 20.sp,
                )
            }
            Spacer(Modifier.size(width = 12.dp, height = 0.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    color = Color(0xFF1A1A1A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = action.subtitle,
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                )
            }
        }

        // 关联条目列表
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "该主题暂无内容",
                    color = Color(0xFF999999),
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items) { item ->
                    HotTopicRow(item)
                }
            }
        }
    }
}

@Composable
private fun HotTopicRow(item: HotListItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HotRowBackground(item.rank))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.rank.toString(),
            color = rankColor(item.rank),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.size(width = 28.dp, height = 24.dp),
        )
        Text(
            text = item.title,
            color = Color(0xFF1A1A1A),
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(width = 8.dp, height = 0.dp))
        item.badge.toLabel()?.let { badge ->
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badge.background)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badge.text,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun HotBadge.toLabel(): BadgeLabel? = when (this) {
    HotBadge.Fire -> BadgeLabel("热", Color(0xFFFF4757))
    HotBadge.Boom -> BadgeLabel("爆", Color(0xFFFF3B30))
    HotBadge.Hot -> BadgeLabel("热", Color(0xFFFF6B6B))
    HotBadge.New -> BadgeLabel("新", Color(0xFFFFA502))
    HotBadge.Rumor -> BadgeLabel("辟谣", Color(0xFF4A90E2))
    HotBadge.None -> null
}

private data class BadgeLabel(val text: String, val background: Color)

private fun rankColor(rank: Int): Color = when (rank) {
    1 -> Color(0xFFFF3B30)
    2 -> Color(0xFFFFA940)
    3 -> Color(0xFFFAAD14)
    else -> Color(0xFF999999)
}

private fun HotRowBackground(rank: Int): Color = when (rank) {
    1, 2, 3 -> Color(0xFFFFF1F0)
    else -> Color.White
}