package com.example.toutiao.presentation.mall

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
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
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// MallScreen — "商城"页（仿头条商城）
//
// 设计参照：design/商城界面.jpg
// 核心区域：
//   1. 顶部红色标题栏（直播/商城/国家补贴 + 搜索栏 + 购物车）
//   2. 4 个功能入口（我的订单 / 签到领钱 / 券与红包 / 关注店铺）
//   3. 新人专享倒计时卡片
//   4. 商品瀑布流（无限复活 / 剑魔 专 / 指挥官无限 / 游戏堡 / 抽茅台）
// =============================================================================
@Composable
fun MallScreen() {
    Scaffold(
        containerColor = Background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { MallTopBar() }
            item { SearchRow() }
            item { QuickEntriesRow() }
            item { Spacer(Modifier.height(8.dp)) }
            item { NewbieCard() }
            item { Spacer(Modifier.height(8.dp)) }
            item { HotProductsSection() }
        }
    }
}

@Composable
private fun MallTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedMain)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "直播",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(20.dp))
        Text("商城", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(20.dp))
        Text("国家补贴", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedMain)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocalOffer,
                    contentDescription = null,
                    tint = Color(0xFFFF5757),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("无畏契约法杖", color = Color(0xFFBBBBBB), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .clickable { /* TODO: 搜索 */ }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("搜索", color = RedMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("🛒", fontSize = 18.sp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF4D4F)),
                contentAlignment = Alignment.Center,
            ) {
                Text("3", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickEntriesRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickEntry("我的订单", Icons.Filled.Receipt, RedMain, badge = "7.0元")
        QuickEntry("签到领钱", Icons.Filled.CardGiftcard, RedMain, badge = "1")
        QuickEntry("券与红包", Icons.Filled.LocalOffer, RedMain)
        QuickEntry("关注店铺", Icons.Filled.Store, RedMain, badge = "3")
    }
}

@Composable
private fun QuickEntry(
    label: String,
    icon: ImageVector,
    tint: Color,
    badge: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { /* TODO: 跳转 */ },
    ) {
        Box {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF4D4F))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFF333333), fontSize = 11.sp)
    }
}

@Composable
private fun NewbieCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFFE7D6), Color(0xFFFFF8EE)),
                ),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("新人专享", color = Color(0xFFFF5757), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("13", color = Color(0xFFFF5757), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(":", color = Color(0xFF333333), fontSize = 12.sp)
                    Text("50", color = Color(0xFFFF5757), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(":", color = Color(0xFF333333), fontSize = 12.sp)
                    Text("44", color = Color(0xFFFF5757), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("¥2.7", "¥2.9", "¥1.99", "¥1.8")) { price ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE0E0E0)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(price, color = Color(0xFFFF5757), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HotProductsSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Text("为你喜欢", color = Color(0xFF333333), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProductCard(
                title = "逆袭中队 鞋链",
                price = "¥155",
                soldCount = "已售 34.2万",
                modifier = Modifier.weight(1f),
            )
            ProductCard(
                title = "剑魔 专 笔记本",
                price = "¥0.99",
                soldCount = "已售 7万",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProductCard(
                title = "指挥官无限契",
                price = "¥9.9",
                soldCount = "已售 1.2万",
                modifier = Modifier.weight(1f),
            )
            ProductCard(
                title = "游戏堡 抽茅台",
                price = "¥2888",
                soldCount = "已售 0.6万",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProductCard(
    title: String,
    price: String,
    soldCount: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE8E8E8)),
            contentAlignment = Alignment.Center,
        ) {
            Text("商品图", color = Color(0xFFAAAAAA), fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color(0xFF333333), fontSize = 12.sp, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(price, color = RedMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(soldCount, color = Color(0xFF999999), fontSize = 10.sp)
    }
}

