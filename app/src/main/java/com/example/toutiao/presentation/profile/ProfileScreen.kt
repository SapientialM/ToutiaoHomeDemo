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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
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
fun ProfileScreen(
    onNotificationsClick: () -> Unit = {},
    onWalletClick: () -> Unit = {},
    onOrderClick: () -> Unit = {},
    onCreatorClick: () -> Unit = {},
    onTasksClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onBookshelfClick: () -> Unit = {},
    onAllFunctionsClick: () -> Unit = {},
) {
    Scaffold(
        containerColor = Background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { TopActionBar(onNotificationsClick = onNotificationsClick) }
            item { UserHeaderSection() }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                GridFunctions2x2(
                    onOrderClick = onOrderClick,
                    onCreatorClick = onCreatorClick,
                    onTasksClick = onTasksClick,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                QuickIconRow(
                    onHistoryClick = onHistoryClick,
                    onBookshelfClick = onBookshelfClick,
                    onAllFunctionsClick = onAllFunctionsClick,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { WalletSection(onClick = onWalletClick) }
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
private fun TopActionBar(onNotificationsClick: () -> Unit) {
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
                        .clickable { onNotificationsClick() },
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
                    // 设计稿 pageTitle 17sp Bold
                    Text(
                        DemoUser.nickname,
                        color = Color(0xFF1A1A1A),
                        fontSize = 17.sp,
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
                Spacer(Modifier.height(4.dp))
                Text(
                    DemoUser.bio,
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatItem(count = formatCount(DemoUser.following), label = "关注")
                    Spacer(Modifier.width(20.dp))
                    StatItem(count = formatCount(DemoUser.follower), label = "粉丝")
                    Spacer(Modifier.width(20.dp))
                    StatItem(count = formatCount(DemoUser.likeCount), label = "获赞")
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { /* TODO: 申请认证 */ },
                ) {
                    Text(DemoUser.verifiedLabel, color = Color(0xFF666666), fontSize = 13.sp)
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
                    .background(Color(DemoUser.avatarBgColor)),
                contentAlignment = Alignment.Center,
            ) {
                Text(DemoUser.avatarEmoji, fontSize = 40.sp)
            }
        }
    }
}

/** 1234 → "1,234", 12345 → "1.2万" */
private fun formatCount(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> "%,d".format(n)
    n < 100_000 -> "%.1f万".format(n / 10_000.0)
    else -> "%.0f万".format(n / 10_000.0)
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
private fun WalletSection(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("我的钱包", color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("查看全部", color = Color(0xFF999999), fontSize = 12.sp)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            // 3x2 钱包格
            Column {
                WalletItems.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        rowItems.forEach { w ->
                            WalletItemCell(w, modifier = Modifier.weight(1f))
                        }
                    }
                    if (rowItems != WalletItems.chunked(3).last()) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletItemCell(item: WalletItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.icon, fontSize = 16.sp)
            Spacer(Modifier.width(4.dp))
            Text(item.label, color = Color(0xFF666666), fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(item.value, color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (item.badge != null) {
            Spacer(Modifier.height(2.dp))
            Text(item.badge, color = RedMain, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun GridFunctions2x2(
    onOrderClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onCreatorClick: () -> Unit = {},
    onTasksClick: () -> Unit = {},
) {
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
                    onClick = onOrderClick,
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
                    onClick = onNotificationsClick,
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
                    onClick = onCreatorClick,
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
                    onClick = onTasksClick,
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
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 设计稿 menuTitle 14sp Medium
            Text(title, color = Color(0xFF1A1A1A), fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
        // 设计稿 menuDesc 11sp
        Text(subtitle, color = Color(0xFF999999), fontSize = 11.sp)
    }
}

@Composable
private fun QuickIconRow(
    onHistoryClick: () -> Unit = {},
    onBookshelfClick: () -> Unit = {},
    onAllFunctionsClick: () -> Unit = {},
) {
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
            QuickIcon(Icons.Filled.History, "浏览历史", onClick = onHistoryClick)
            QuickIcon(Icons.Filled.Book, "书架", onClick = onBookshelfClick)
            QuickIcon(Icons.Filled.PlaylistPlay, "在看短剧", onClick = { /* TODO: 看短剧 */ })
            QuickIcon(Icons.Filled.MoreHoriz, "全部功能", onClick = onAllFunctionsClick)
        }
    }
}

@Composable
private fun QuickIcon(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
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
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color(0xFFD9F0D2), Color(0xFFE8F5DD), Color(0xFFF1F7E5)),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 设计稿 bannerTitle 15sp Bold
                    Text("6月幸运签", color = Color(0xFF4FB69C), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("· 全新签文来袭", color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                // 设计稿 bannerDesc 11sp
                Text("每日抽签分15000元", color = Color(0xFF666666), fontSize = 11.sp)
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
                    // 设计稿 sectionTitle 15sp Medium
                    Text("完成挑战得奖励", color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
    val tabs = remember { listOf("作品", "收藏", "赞过", "短剧", "草稿", "推荐") }
    val subTabs = remember { listOf("全部", "相册", "转发") }

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

        // Tab 内容
        WorksTabContent(
            selectedTab = tabs[selectedTabIndex],
            selectedSubTab = subTabs[selectedSubTabIndex],
        )
    }
}

/**
 * 作品 Tab 内容区: 根据选中 Tab 渲染 sample data
 *
 * 6 个 tab 全部填上合成 sample data (不再是空状态占位):
 * - 作品 / 草稿 → SamplePosts / SampleDrafts
 * - 收藏 / 赞过 → SampleFavorites / SampleLiked
 * - 短剧 → SampleDramas
 * - 推荐 → 系统推荐 (复用 SamplePosts 渲染)
 */
@Composable
private fun WorksTabContent(
    selectedTab: String,
    selectedSubTab: String,
) {
    when (selectedTab) {
        "作品" -> PostsList(SamplePosts)
        "收藏" -> FavoritesList(SampleFavorites)
        "赞过" -> FavoritesList(SampleLiked)
        "短剧" -> DramasList(SampleDramas)
        "草稿" -> PostsList(SampleDrafts, isDraft = true)
        else -> PostsList(SamplePosts.take(2), recommendedHint = true)
    }
}

@Composable
private fun PostsList(posts: List<UserPost>, isDraft: Boolean = false, recommendedHint: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (recommendedHint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Explore, contentDescription = null, tint = RedMain, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("根据你的兴趣推荐", color = Color(0xFF666666), fontSize = 12.sp)
            }
        }
        posts.forEachIndexed { idx, post ->
            PostItem(post, isDraft)
            if (idx < posts.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(0.5.dp)
                        .background(Color(0xFFEEEEEE)),
                )
            }
        }
    }
}

@Composable
private fun PostItem(post: UserPost, isDraft: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(DemoUser.avatarBgColor)),
                contentAlignment = Alignment.Center,
            ) {
                Text(DemoUser.avatarEmoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(DemoUser.nickname, color = Color(0xFF1A1A1A), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(post.timeText, color = Color(0xFF999999), fontSize = 11.sp)
            }
            if (isDraft) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF1E6))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("草稿", color = Color(0xFFFF8C42), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (post.title.isNotBlank() && post.title != "(无标题)") post.title else post.content,
            color = Color(0xFF1A1A1A),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 4,
        )
        if (post.title.isNotBlank() && post.title != "(无标题)" && post.content.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(post.content, color = Color(0xFF666666), fontSize = 13.sp, maxLines = 2, lineHeight = 18.sp)
        }
        if (post.imageUrl != null) {
            Spacer(Modifier.height(8.dp))
            coil.compose.AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFF999999), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("${post.likeCount}", color = Color(0xFF999999), fontSize = 11.sp)
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Filled.Bookmark, contentDescription = null, tint = Color(0xFF999999), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("${post.commentCount}", color = Color(0xFF999999), fontSize = 11.sp)
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Filled.Share, contentDescription = null, tint = Color(0xFF999999), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("${post.shareCount}", color = Color(0xFF999999), fontSize = 11.sp)
        }
    }
}

@Composable
private fun FavoritesList(favs: List<UserFavorite>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        favs.forEachIndexed { idx, fav ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* TODO: 跳转详情 */ }
                    .padding(12.dp),
            ) {
                coil.compose.AsyncImage(
                    model = fav.newsCover,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(fav.newsTitle, color = Color(0xFF1A1A1A), fontSize = 14.sp, maxLines = 2, lineHeight = 20.sp)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fav.newsSource, color = Color(0xFF999999), fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(fav.savedAt, color = Color(0xFF999999), fontSize = 11.sp)
                    }
                }
            }
            if (idx < favs.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(0.5.dp)
                        .background(Color(0xFFEEEEEE)),
                )
            }
        }
    }
}

@Composable
private fun DramasList(dramas: List<UserDrama>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        dramas.chunked(2).forEach { rowDramas ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowDramas.forEach { d ->
                    DramaCard(d, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DramaCard(d: UserDrama, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1A1A1A)),
    ) {
        coil.compose.AsyncImage(
            model = d.cover,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
        // 进度条
        val progress = d.latestEpisode.toFloat() / d.totalEpisodes
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(6.dp),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0x66FFFFFF))
                        .clip(RoundedCornerShape(1.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(2.dp)
                            .background(RedMain),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    d.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Row {
                    Text("更新至第 ${d.latestEpisode} 集", color = Color(0xCCFFFFFF), fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text(d.durationLabel, color = if (d.durationLabel.startsWith("剩")) RedMain else Color(0xFFFFC83A), fontSize = 10.sp)
                }
            }
        }
    }
}
