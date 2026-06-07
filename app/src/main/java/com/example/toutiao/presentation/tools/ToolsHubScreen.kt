package com.example.toutiao.presentation.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.presentation.mall.sub.TopBar
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// AllFunctionsScreen — 全部功能 (我的页 quick icon "全部功能")
//
// 4x2 网格, 16 个功能入口, 涵盖头条 demo 全部主要功能
// =============================================================================
@Composable
fun AllFunctionsScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = { TopBar(title = "全部功能", onBack = onBack) },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(AllFunctions) { fn ->
                FunctionGridItem(fn)
            }
        }
    }
}

data class FunctionItem(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val onClick: () -> Unit = {},
)

val AllFunctions: List<FunctionItem> = listOf(
    FunctionItem(Icons.Filled.Receipt, "订单", RedMain),
    FunctionItem(Icons.Filled.LocalOffer, "优惠券", Color(0xFFFF8C42)),
    FunctionItem(Icons.Filled.Store, "关注店铺", Color(0xFF7B61FF)),
    FunctionItem(Icons.Filled.MonetizationOn, "金币任务", Color(0xFFFFC83A)),
    FunctionItem(Icons.Filled.Bookmark, "我的收藏", Color(0xFF1B7FE0)),
    FunctionItem(Icons.Filled.History, "浏览历史", Color(0xFF4FB69C)),
    FunctionItem(Icons.Filled.Headphones, "听头条", Color(0xFFEF6C8A)),
    FunctionItem(Icons.Filled.PlaylistPlay, "在看短剧", Color(0xFF7B61FF)),
    FunctionItem(Icons.Filled.Message, "消息", RedMain),
    FunctionItem(Icons.Filled.Notifications, "通知", Color(0xFFFF8C42)),
    FunctionItem(Icons.Filled.Edit, "创作中心", Color(0xFF1B7FE0)),
    FunctionItem(Icons.Filled.Diamond, "会员中心", Color(0xFFFFC83A)),
    FunctionItem(Icons.Filled.PersonAdd, "邀请好友", Color(0xFF4FB69C)),
    FunctionItem(Icons.Filled.Favorite, "我的赞", Color(0xFFEF6C8A)),
    FunctionItem(Icons.Filled.Share, "分享有礼", Color(0xFFFF8C42)),
    FunctionItem(Icons.Filled.Settings, "设置", Color(0xFF999999)),
)

@Composable
private fun FunctionGridItem(item: FunctionItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { item.onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(item.tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = item.tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(item.label, color = Color(0xFF1A1A1A), fontSize = 12.sp)
    }
}

// =============================================================================
// HistoryScreen — 浏览历史 (我的页 quick icon "浏览历史")
// =============================================================================
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = { TopBar(title = "浏览历史", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("近 7 天浏览 28 条", color = Color(0xFF666666), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("清空", color = RedMain, fontSize = 12.sp,
                    modifier = Modifier.clickable { /* TODO: 清空 */ })
            }
            // 复用 favorites list (sample data 够用, 不另写)
            com.example.toutiao.presentation.profile.SampleFavorites.take(8).forEach { fav ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .clickable { /* TODO: 跳转详情 */ }
                        .padding(12.dp),
                ) {
                    coil.compose.AsyncImage(
                        model = fav.newsCover,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
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
            }
        }
    }
}

// =============================================================================
// BookshelfScreen — 书架 (我的页 quick icon "书架")
// =============================================================================
@Composable
fun BookshelfScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = { TopBar(title = "书架", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFEDED)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Book, contentDescription = null, tint = RedMain, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("我的书架", color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("收藏 24 本 · 12 本在读", color = Color(0xFF999999), fontSize = 11.sp)
                }
                Text("管理", color = RedMain, fontSize = 12.sp)
            }
            // 复用 favorites + dramas 拼成"书架"展示
            com.example.toutiao.presentation.profile.SampleFavorites.take(6).forEachIndexed { idx, fav ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(Color.White)
                        .clickable { /* TODO */ }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${idx + 1}",
                        color = RedMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    coil.compose.AsyncImage(
                        model = fav.newsCover,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fav.newsTitle, color = Color(0xFF1A1A1A), fontSize = 13.sp, maxLines = 2, lineHeight = 18.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("已读 80%", color = Color(0xFF999999), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
