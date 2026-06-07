package com.example.toutiao.presentation.profile

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.Divider
import com.example.toutiao.ui.theme.RedMain
import com.example.toutiao.ui.theme.TextHint

// =============================================================================
// ProfileScreen — "我的"页（仿头条我的页面）
//
// 设计参照：design/我的.jpg
// 核心区域：
//   1. 顶部操作栏（发布按钮 + 4 个图标）
//   2. 用户信息区（用户名 + 头像 + 关注/粉丝/获赞 + 申请认证）
//   3. 2x2 功能区（购物/订单、消息私信、创作中心、任务）
//   4. 4 个快捷入口（浏览历史/书架/在看短剧/全部功能）
//   5. 6月幸运签
//   6. 完成挑战得奖励
//   7. Tab 区（作品/收藏/赞过/短剧/草稿/推荐 + 全部/相册/转发 + 搜索/耳机）
// =============================================================================
@Composable
fun ProfileScreen() {
    Scaffold(
        containerColor = Background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { TopActionBar() }
            item { UserHeaderSection() }
            item { Spacer(Modifier.height(8.dp)) }
            item { GridFunctions2x2() }
            item { Spacer(Modifier.height(8.dp)) }
            item { QuickIconRow() }
            item { Spacer(Modifier.height(8.dp)) }
            item { LuckySignCard() }
            item { Spacer(Modifier.height(8.dp)) }
            item { ChallengeCard() }
            item { Spacer(Modifier.height(8.dp)) }
            item { WorksTabSection() }
        }
    }
}

@Composable
private fun TopActionBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .clickable { /* TODO: 发布 */ }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("发布", color = Color(0xFF333333), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopIconButton(Icons.Filled.PersonAdd, "添加好友")
            Box(modifier = Modifier.size(22.dp)) {
                Icon(
                    imageVector = Icons.Filled.NotificationsNone,
                    contentDescription = "消息",
                    tint = Color(0xFF333333),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { /* TODO: 消息 */ },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF4D4F))
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("1", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            TopIconButton(Icons.Filled.Share, "分享")
            TopIconButton(Icons.Filled.Settings, "设置")
        }
    }
}

@Composable
private fun TopIconButton(icon: ImageVector, contentDescription: String) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = Color(0xFF333333),
        modifier = Modifier
            .size(22.dp)
            .clickable { /* TODO: 跳转 */ },
    )
}

@Composable
private fun UserHeaderSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "用户4770313",
                        color = Color(0xFF1A1A1A),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatItem(count = "0", label = "关注")
                    Spacer(Modifier.width(20.dp))
                    StatItem(count = "0", label = "粉丝")
                    Spacer(Modifier.width(20.dp))
                    StatItem(count = "0", label = "获赞")
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { /* TODO: 申请认证 */ },
                ) {
                    Text("申请认证", color = Color(0xFF666666), fontSize = 13.sp)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4FB69C)),
                contentAlignment = Alignment.Center,
            ) {
                Text("👹", fontSize = 48.sp)
            }
        }
    }
}

@Composable
private fun StatItem(count: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(count, color = Color(0xFF1A1A1A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(3.dp))
        Text(label, color = Color(0xFF666666), fontSize = 13.sp, modifier = Modifier.padding(bottom = 1.dp))
    }
}

@Composable
private fun GridFunctions2x2() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White),
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                FunctionCard(
                    title = "购物/订单",
                    subtitle = "查看低价好物",
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(72.dp)
                        .background(Divider),
                )
                FunctionCard(
                    title = "消息私信",
                    subtitle = "查看我的消息",
                    badge = "3",
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Divider),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                FunctionCard(
                    title = "创作中心",
                    subtitle = "查看我的创作中心",
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(72.dp)
                        .background(Divider),
                )
                FunctionCard(
                    title = "任务",
                    subtitle = "签到开宝箱赚金币",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FunctionCard(
    title: String,
    subtitle: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable { /* TODO: 跳转 */ }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (badge != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RedMain)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color(0xFF999999), fontSize = 12.sp)
    }
}

@Composable
private fun QuickIconRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QuickIcon(Icons.Filled.History, "浏览历史")
            QuickIcon(Icons.Filled.Book, "书架")
            QuickIcon(Icons.Filled.PlaylistPlay, "在看短剧")
            QuickIcon(Icons.Filled.MoreHoriz, "全部功能")
        }
    }
}

@Composable
private fun QuickIcon(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { /* TODO: 跳转 */ },
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFF333333), modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFF333333), fontSize = 12.sp)
    }
}

@Composable
private fun LuckySignCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFD9F0D2)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("6月幸运签", color = Color(0xFF4FB69C), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("· 全新签文来袭", color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("每日抽签分15000元", color = Color(0xFF666666), fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF4FB69C))
                    .clickable { /* TODO: 抽签 */ }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("去抽签", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChallengeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("完成挑战得奖励", color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("发日常微头条笔记", color = Color(0xFF666666), fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(">15字且带图和定位，得", color = Color(0xFF999999), fontSize = 12.sp)
                    Text("0.3元", color = RedMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable { /* TODO: 去完成 */ }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("去完成", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun WorksTabSection() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedSubTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("作品", "收藏", "赞过", "短剧", "草稿", "推荐")
    val subTabs = listOf("全部", "相册", "转发")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedTabIndex
                Column(
                    modifier = Modifier
                        .clickable { selectedTabIndex = index }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        label,
                        color = if (selected) Color(0xFF1A1A1A) else Color(0xFF999999),
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(20.dp)
                                .background(RedMain),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            subTabs.forEachIndexed { index, label ->
                val selected = index == selectedSubTabIndex
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFFEEEEEE) else Color(0xFFF5F5F5))
                        .clickable { selectedSubTabIndex = index }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        label,
                        color = if (selected) Color(0xFF1A1A1A) else Color(0xFF666666),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.Search,
                contentDescription = "搜索",
                tint = Color(0xFF666666),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.Filled.Headphones,
                contentDescription = "耳机",
                tint = Color(0xFF666666),
                modifier = Modifier.size(18.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF5F5F5))
                        .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.HourglassEmpty,
                        contentDescription = null,
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
