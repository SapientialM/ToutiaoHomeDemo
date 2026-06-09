package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.RedMain

/**
 * 关注作者卡片
 */
data class FollowAuthor(
    val avatar: String,
    val name: String,
    val meta: String,  // 粉丝数 / 简介
    val avatarColor: Color,
    val postCount: Int,  // 0 = 推荐关注；>0 = 关注作者发的帖数
)

/**
 * MVPTask #7: 关注作者图文新闻（2-3 条）
 */
@Composable
fun FollowAuthorSection(
    authors: List<FollowAuthor>,
    modifier: Modifier = Modifier,
    onItemClick: (FollowAuthor) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp),
    ) {
        authors.forEachIndexed { idx, author ->
            FollowAuthorItem(author = author, onClick = { onItemClick(author) })
            if (idx < authors.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFF0F0F0)),
                )
            }
        }
    }
}

@Composable
private fun FollowAuthorItem(author: FollowAuthor, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(author.avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = author.avatar,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = author.name,
                    color = Color(0xFF1A1A1A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = author.meta,
                    color = Color(0xFF999999),
                    fontSize = 11.sp,
                )
            }
            // 已关注按钮（灰色边框）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "已关注",
                        color = Color(0xFF999999),
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (author.postCount > 0) {
            Spacer(Modifier.height(8.dp))
            // 模拟作者发的图文
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFE4B5),
                                Color(0xFFFFB6C1),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("📝 作者最新图文", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
    }
}

/**
 * MVPTask #7: 轮播"你可能感兴趣的人"
 */
@Composable
fun FollowInterestCarousel(modifier: Modifier = Modifier) {
    val interests = listOf(
        FollowAuthor("🎬", "电影情报局", "影评 / 8.2万", Color(0xFF6C5CE7), 0),
        FollowAuthor("📚", "读书笔记", "书评 / 5.6万", Color(0xFF00B894), 0),
        FollowAuthor("🍜", "深夜食堂", "美食 / 12.1万", Color(0xFFE17055), 0),
        FollowAuthor("✈️", "旅行日记", "游记 / 7.3万", Color(0xFF0984E3), 0),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "更多内容",
                color = Color(0xFF1A1A1A),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "你可能感兴趣的人",
                color = Color(0xFF999999),
                fontSize = 12.sp,
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(interests, key = { it.name }) { author ->
                InterestCard(author = author)
            }
        }
    }
}

@Composable
private fun InterestCard(author: FollowAuthor) {
    var followed by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { followed = !followed },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(author.avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(author.avatar, fontSize = 26.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = author.name,
            color = Color(0xFF1A1A1A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = author.meta,
            color = Color(0xFF999999),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (followed) Color(0xFFF5F5F5) else RedMain)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!followed) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    text = if (followed) "已关注" else "关注",
                    color = if (followed) Color(0xFF999999) else Color.White,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * MVPTask #7: 推荐账号（带"关注"按钮）
 */
@Composable
fun FollowAuthorRecommendRow(
    author: FollowAuthor,
    modifier: Modifier = Modifier,
) {
    var followed by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(author.avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(author.avatar, fontSize = 22.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = author.name,
                color = Color(0xFF1A1A1A),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = author.meta,
                color = Color(0xFF999999),
                fontSize = 11.sp,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (followed) Color(0xFFF5F5F5) else RedMain)
                .clickable { followed = !followed }
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                text = if (followed) "已关注" else "+ 关注",
                color = if (followed) Color(0xFF999999) else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
