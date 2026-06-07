package com.example.toutiao.presentation.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.presentation.profile.NotifQuickAction
import com.example.toutiao.presentation.profile.NotifQuickActions
import com.example.toutiao.presentation.profile.NotificationCategory
import com.example.toutiao.presentation.profile.NotificationItem
import com.example.toutiao.presentation.profile.SampleNotifications
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.RedMain
import com.example.toutiao.ui.theme.TextHint

// =============================================================================
// NotificationScreen — 消息中心
//
// 来源: 我的页顶部 消息 图标 (带红点 1)
//
// 结构:
//   1. 顶部标题栏 (返回 + 全部已读)
//   2. 4 个快捷入口 (赞和评论 / 新粉丝 / 系统通知 / 商城消息) + 未读数
//   3. 5 大类分组 (互动 / 系统 / 视频 / 收益 / 商城) 各带 1 个示例
// =============================================================================
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Background,
        topBar = { NotifTopBar(onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { QuickActionsRow() }
            item { Spacer(Modifier.height(8.dp)) }
            SampleNotifications.forEach { category ->
                item(key = "cat_${category.type.name}") {
                    CategorySection(category)
                }
            }
        }
    }
}

@Composable
private fun NotifTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowBack,
            contentDescription = "返回",
            tint = Color(0xFF1A1A1A),
            modifier = Modifier
                .size(24.dp)
                .clickable { onBack() },
        )
        Spacer(Modifier.width(8.dp))
        Text("消息", color = Color(0xFF1A1A1A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("全部已读", color = Color(0xFF666666), fontSize = 13.sp,
                modifier = Modifier.clickable { /* TODO: 标记全部已读 */ })
        }
    }
}

@Composable
private fun QuickActionsRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NotifQuickActions.forEach { action ->
            QuickActionItem(action)
        }
    }
}

@Composable
private fun QuickActionItem(action: NotifQuickAction) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { /* TODO: 进入对应分类 */ },
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center,
            ) {
                Text(action.icon, fontSize = 24.sp)
            }
            if (action.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(RedMain)
                        .border(1.5.dp, Color.White, CircleShape)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (action.unreadCount > 99) "99+" else action.unreadCount.toString(),
                        color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(action.label, color = Color(0xFF1A1A1A), fontSize = 12.sp)
    }
}

@Composable
private fun CategorySection(category: NotificationCategory) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(vertical = 4.dp),
    ) {
        // 分类头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(category.type.label, color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("查看全部", color = TextHint, fontSize = 12.sp,
                modifier = Modifier.clickable { /* TODO: 查看全部 */ })
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextHint,
                modifier = Modifier.size(14.dp),
            )
        }
        category.items.forEach { item ->
            NotifRow(item)
            if (item != category.items.last()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 64.dp)
                        .height(0.5.dp)
                        .background(Color(0xFFEEEEEE)),
                )
            }
        }
    }
}

@Composable
private fun NotifRow(item: NotificationItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: 跳转 */ }
            .background(if (item.isRead) Color.White else Color(0xFFFFF7F7))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 头像/类型圆
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(item.avatarBg)),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.avatarEmoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    color = Color(0xFF1A1A1A),
                    fontSize = 14.sp,
                    fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (!item.isRead) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RedMain),
                    )
                }
            }
            if (item.content.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    item.content,
                    color = Color(0xFF666666),
                    fontSize = 13.sp,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.timeText, color = TextHint, fontSize = 11.sp)
                if (item.actionLabel != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFEDED))
                            .clickable { /* TODO: 跳转 */ }
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(item.actionLabel, color = RedMain, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

