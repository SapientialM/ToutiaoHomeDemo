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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.toutiao.presentation.common.rememberImageError
import com.example.toutiao.presentation.common.rememberImagePlaceholder
import com.example.toutiao.presentation.profile.HotAuthor
import com.example.toutiao.presentation.profile.HotAuthors
import com.example.toutiao.ui.theme.RedMain

/**
 * 热门作者横向轮播 (推荐 tab 顶部第二项)
 *
 * 数据源: ProfileMockData.HotAuthors (6 位合成作者, 头像/粉丝数/最新动态)
 */
@Composable
fun HotAuthorsRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "你可能想关注",
                color = Color(0xFF1A1A1A),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "查看全部",
                color = Color(0xFF999999),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(HotAuthors, key = { it.userId }) { author ->
                AuthorCard(author)
            }
        }
    }
}

@Composable
private fun AuthorCard(author: HotAuthor) {
    val placeholder = rememberImagePlaceholder()
    val errorPainter = rememberImageError()
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable { /* TODO: 进作者主页 */ },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 头像
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(author.avatarBg)),
            contentAlignment = Alignment.Center,
        ) {
            Text(author.avatarEmoji, fontSize = 30.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            author.nickname,
            color = Color(0xFF1A1A1A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            "${formatFollowers(author.followers)} 粉丝",
            color = Color(0xFF999999),
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(6.dp))
        // 最新动态缩略图
        AsyncImage(
            model = author.latestPostCover,
            contentDescription = null,
            placeholder = placeholder,
            error = errorPainter,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            author.latestPostTitle,
            color = Color(0xFF666666),
            fontSize = 10.sp,
            maxLines = 2,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        // 关注按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RedMain.copy(alpha = 0.1f))
                .clickable { /* TODO: 关注 */ },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (author.isFollowed) "已关注" else "+ 关注",
                color = RedMain,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatFollowers(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> "%,d".format(n)
    n < 10_000_000 -> "%.1f万".format(n / 10_000.0)
    else -> "%.0f万".format(n / 10_000.0)
}
