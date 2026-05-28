package com.example.toutiao.presentation.video

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.presentation.home.components.VideoCard

@Composable
fun VideoScreen() {
    // 先写死 3 条 Mock 视频数据
    val videoList = listOf(
        FeedCard.Video(
            id = "v1",
            title = "猫咪玩水的 100 种姿势",
            source = "萌宠频道",
            commentCount = 3421,
            publishTime = "2小时前",
            imageUrl = "https://picsum.photos/seed/video1/800/450",
            videoUrl = "",
            duration = "03:45",
        ),
        FeedCard.Video(
            id = "v2",
            title = "深度解读：2026 年科技趋势",
            source = "科技日报",
            commentCount = 8902,
            publishTime = "5小时前",
            imageUrl = "https://picsum.photos/seed/video2/800/450",
            videoUrl = "",
            duration = "12:30",
        ),
        FeedCard.Video(
            id = "v3",
            title = "家常菜教学：红烧肉正宗做法",
            source = "美食台",
            commentCount = 1205,
            publishTime = "昨天",
            imageUrl = "https://picsum.photos/seed/video3/800/450",
            videoUrl = "",
            duration = "08:15",
        ),
    )

    Scaffold(
        topBar = {
            // 简单顶部栏：白色背景，红色标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "视频",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD81E06),
                )
            }
        },
        containerColor = Color(0xFFF5F5F5),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(videoList, key = { it.id }) { card ->
                VideoCard(card = card)
            }
        }
    }
}