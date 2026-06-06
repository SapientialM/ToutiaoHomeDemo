package com.example.toutiao.presentation.task

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// TaskScreen — 任务中心页
// =============================================================================
@Composable
fun TaskScreen() {
    Scaffold(
        topBar = { TaskTopBar() },
        containerColor = Color(0xFFF5F5F5),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // 金币和进度卡片
            item {
                TaskSummaryCard()
            }

            item { Spacer(Modifier.height(8.dp)) }

            // 每日任务列表
            item {
                TaskListSection()
            }

            item { Spacer(Modifier.height(8.dp)) }

            // 成就徽章
            item {
                AchievementSection()
            }
        }
    }
}

@Composable
private fun TaskTopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedMain)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "任务中心",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TaskSummaryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        ) {
            CoinDisplay(
                icon = Icons.Filled.MonetizationOn,
                value = "1,280",
                label = "我的金币",
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(Color(0xFFEEEEEE)),
            )
            CoinDisplay(
                icon = Icons.Filled.EmojiEvents,
                value = "12",
                label = "今日任务",
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(Color(0xFFEEEEEE)),
            )
            CoinDisplay(
                icon = Icons.Filled.CheckCircle,
                value = "8",
                label = "已完成",
            )
        }

        Spacer(Modifier.height(16.dp))

        // 进度条
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "今日任务进度",
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                )
                Text(
                    text = "8/12",
                    fontSize = 13.sp,
                    color = RedMain,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { 8f / 12f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = RedMain,
                trackColor = Color(0xFFEEEEEE),
            )
        }
    }
}

@Composable
private fun CoinDisplay(
    icon: ImageVector,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = RedMain,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF999999),
        )
    }
}

private data class TaskItem(
    val id: String,
    val title: String,
    val description: String,
    val reward: String,
    val progress: String,
    val isCompleted: Boolean,
    val icon: ImageVector,
)

private val dailyTasks = listOf(
    TaskItem(
        id = "t1",
        title = "阅读新闻",
        description = "阅读10篇新闻资讯",
        reward = "+50金币",
        progress = "7/10",
        isCompleted = false,
        icon = Icons.Filled.LocalFireDepartment,
    ),
    TaskItem(
        id = "t2",
        title = "分享文章",
        description = "分享3篇文章给好友",
        reward = "+30金币",
        progress = "3/3",
        isCompleted = true,
        icon = Icons.Filled.TaskAlt,
    ),
    TaskItem(
        id = "t3",
        title = "观看视频",
        description = "观看5个视频内容",
        reward = "+40金币",
        progress = "2/5",
        isCompleted = false,
        icon = Icons.Filled.LocalFireDepartment,
    ),
    TaskItem(
        id = "t4",
        title = "签到打卡",
        description = "每日登录签到",
        reward = "+20金币",
        progress = "1/1",
        isCompleted = true,
        icon = Icons.Filled.CheckCircle,
    ),
)

@Composable
private fun TaskListSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
    ) {
        Text(
            text = "每日任务",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
        )
        Spacer(Modifier.height(12.dp))
        dailyTasks.forEachIndexed { index, task ->
            TaskRow(task = task)
            if (index < dailyTasks.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp)
                        .height(1.dp)
                        .background(Color(0xFFEEEEEE)),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = task.icon,
            contentDescription = null,
            tint = if (task.isCompleted) Color(0xFF4CAF50) else RedMain,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A1A),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${task.description} · ${task.progress}",
                fontSize = 12.sp,
                color = Color(0xFF999999),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = task.reward,
                fontSize = 13.sp,
                color = if (task.isCompleted) Color(0xFF4CAF50) else RedMain,
                fontWeight = FontWeight.Medium,
            )
            if (task.isCompleted) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "已完成",
                    fontSize = 11.sp,
                    color = Color(0xFF4CAF50),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFCCCCCC),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AchievementSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
    ) {
        Text(
            text = "成就徽章",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        ) {
            AchievementBadge("阅读达人", Icons.Filled.LocalFireDepartment, true)
            AchievementBadge("分享之星", Icons.Filled.TaskAlt, true)
            AchievementBadge("签到王者", Icons.Filled.EmojiEvents, false)
            AchievementBadge("视频爱好者", Icons.Filled.CheckCircle, false)
        }
    }
}

@Composable
private fun AchievementBadge(
    name: String,
    icon: ImageVector,
    unlocked: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (unlocked) RedMain.copy(alpha = 0.1f) else Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = if (unlocked) RedMain else Color(0xFFCCCCCC),
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = 12.sp,
            color = if (unlocked) Color(0xFF1A1A1A) else Color(0xFFBBBBBB),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskScreenPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        TaskScreen()
    }
}
