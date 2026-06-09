package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * 畅听频道 - 副 Tab 行
 *
 * 设计稿：3 个 Tab 居中（听头条/听书/听音乐），选中红字+下划线
 */
@Composable
fun AudioSubTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    color = if (selected) RedMain else Color(0xFF333333),
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                if (selected) {
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(16.dp)
                            .background(RedMain),
                    )
                } else {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

/**
 * 畅听频道 - 分区头（热门榜 / 相关推荐）
 *
 * 设计稿：左 18sp Bold 标题 + 右侧子 Tab（完结榜/高分榜 等）
 */
@Composable
fun AudioSectionHeader(
    title: String,
    rightTabs: List<String> = emptyList(),
    selectedRightIndex: Int = 0,
    onRightTabSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color(0xFF1A1A1A),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        if (rightTabs.isNotEmpty()) {
            rightTabs.forEachIndexed { idx, tab ->
                val selected = idx == selectedRightIndex
                Text(
                    text = tab,
                    color = if (selected) RedMain else Color(0xFF999999),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onRightTabSelected(idx) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * 畅听频道 - 双列热门榜卡片
 *
 * 设计稿：64x64 圆角封面（带评分角标）+ 序号 + 标题 + 分类标签 + 收听人数
 */
@Composable
fun AudioHotCard(
    rank: Int,
    coverUrl: String,
    rating: String,
    title: String,
    tag: String,
    listeners: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = rank.toString(),
            color = if (rank <= 3) RedMain else Color(0xFF999999),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE0E0E0)),
        ) {
            // 评分角标
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "$rating 分",
                    color = Color(0xFFFFB028),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF1A1A1A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tag,
                color = Color(0xFF999999),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = listeners,
                color = Color(0xFF999999),
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * 畅听频道 - 分类标签 Chip（全部/总裁/玄幻 等）
 */
@Composable
fun AudioCategoryChips(
    categories: List<String>,
    selectedIndex: Int = 0,
    onCategorySelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categories.forEachIndexed { idx, cat ->
            val selected = idx == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) RedMain else Color(0xFFF5F5F5))
                    .clickable { onCategorySelected(idx) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = cat,
                    color = if (selected) Color.White else Color(0xFF666666),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * 畅听频道 - 相关推荐大卡
 *
 * 设计稿：左侧 80x80 封面 + 右侧标题/描述/标签/评分
 */
@Composable
fun AudioRecommendItem(
    title: String,
    description: String,
    tag: String,
    rating: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE0E0E0)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color(0xFF1A1A1A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$rating 分",
                    color = Color(0xFFFFB028),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                color = Color(0xFF999999),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tag,
                color = Color(0xFF999999),
                fontSize = 11.sp,
            )
        }
    }
}
