package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.RedMain

/**
 * 体育频道 - 赛事分类 chips（直播 / NBA / CBA / 世界杯）
 */
@Composable
fun SportsCategoryChips(
    categories: List<String>,
    selectedIndex: Int = 0,
    onCategorySelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        }
    }
}

/**
 * 体育频道 - 顶部横幅
 *
 * 设计稿：蓝绿渐变足球场背景 + 白色标题
 */
@Composable
fun SportsBanner(
    title: String = "上头条,玩转江苏省城市足球联赛",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E5C8A), Color(0xFF2A8C7A)),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "⚽ 头条体育",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
        // 右下角足球 emoji
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        ) {
            Text("⚽", fontSize = 36.sp)
        }
    }
}

/**
 * 体育频道 - 比赛卡片
 *
 * 设计稿：时间 + 联赛名 + 状态（已结束/进行中）+ 两队 + 比分
 */
@Composable
fun SportsMatchCard(
    match: SportsMatch,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.time,
                    color = Color(0xFF1A1A1A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = match.status,
                    color = if (match.status == "进行中") RedMain else Color(0xFF999999),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = match.league,
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 主队
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(match.homeColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = match.homeTeam,
                        color = Color(0xFF1A1A1A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                // 比分
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = match.homeScore.toString(),
                        color = if (match.homeScore > match.awayScore) RedMain else Color(0xFF1A1A1A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = " : ",
                        color = Color(0xFF999999),
                        fontSize = 14.sp,
                    )
                    Text(
                        text = match.awayScore.toString(),
                        color = if (match.awayScore > match.homeScore) RedMain else Color(0xFF1A1A1A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // 客队
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = match.awayTeam,
                        color = Color(0xFF1A1A1A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(match.awayColor),
                    )
                }
            }
        }
    }
}

/**
 * 体育频道 - 比赛双列
 */
@Composable
fun SportsMatchRow(
    matches: List<SportsMatch>,
    onItemClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        matches.forEachIndexed { idx, match ->
            SportsMatchCard(
                match = match,
                onClick = { onItemClick(idx) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

data class SportsMatch(
    val time: String,
    val status: String,
    val league: String,
    val homeTeam: String,
    val homeScore: Int,
    val awayTeam: String,
    val awayScore: Int,
    val homeColor: Color = Color(0xFF74ACDF),
    val awayColor: Color = Color(0xFF0073CF),
)
