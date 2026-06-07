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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.toutiao.domain.model.Product
import com.example.toutiao.presentation.common.rememberImageError
import com.example.toutiao.presentation.common.rememberImagePlaceholder
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// MallScreen — "商城"页（仿头条商城）
//
// 设计参照：design/商城界面.jpg
// 核心区域：
//   1. 顶部红色渐变标题栏（直播/商城/国家补贴 + 搜索栏 + 购物车）
//   2. 5 个功能入口（我的订单 / 签到领钱 / 券与红包 / 关注店铺 / 购物消息）
//   3. 新人专享倒计时卡片
//   4. 官方商城标签 + 商品双列（4 个产品）
//   5. 你可能喜欢 + 2x2 推荐商品
//   6. 右下角直播悬浮卡
// =============================================================================
@Composable
fun MallScreen(
    viewModel: MallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item { MallTopBar() }
                item { SearchRow() }
                item { QuickEntriesRow() }
                item { Spacer(Modifier.height(8.dp)) }
                item { NewbieCard() }
                item { Spacer(Modifier.height(8.dp)) }
                item { OfficialStoreSection(products = uiState.officialStoreProducts) }
                item { Spacer(Modifier.height(8.dp)) }
                item { RecommendSection(products = uiState.recommendProducts) }
            }
        }
    }
}

@Composable
private fun MallTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF6B6B),
                        Color(0xFFFF8E8E),
                        Color(0xFFFFB1B1),
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 设计稿：3 Tab 16sp，未选中 85% 透明白，选中纯白
        MallTopTab("直播", selected = false)
        Spacer(Modifier.width(20.dp))
        MallTopTab("商城", selected = true)
        Spacer(Modifier.width(20.dp))
        MallTopTab("国家补贴", selected = false)
    }
}

@Composable
private fun MallTopTab(label: String, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        if (selected) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .height(2.dp)
                    .width(20.dp)
                    .background(Color.White),
            )
        }
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
        QuickEntry("购物消息", Icons.Filled.Notifications, RedMain, badge = "3")
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
    val placeholder = rememberImagePlaceholder()
    val errorPainter = rememberImageError()
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
                items(listOf("¥2.7" to "https://picsum.photos/seed/newbie1/120/120", "¥2.9" to "https://picsum.photos/seed/newbie2/120/120", "¥1.99" to "https://picsum.photos/seed/newbie3/120/120", "¥1.8" to "https://picsum.photos/seed/newbie4/120/120")) { (price, url) ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = placeholder,
                            error = errorPainter,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xCCFF5757))
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(price, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HotProductsSection() {
    // 保留旧实现但 unused — 改为 OfficialStoreSection 后未引用
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
                imageUrl = "https://picsum.photos/seed/mall1/400/400",
                modifier = Modifier.weight(1f),
            )
            ProductCard(
                title = "剑魔 专 笔记本",
                price = "¥0.99",
                soldCount = "已售 7万",
                imageUrl = "https://picsum.photos/seed/mall2/400/400",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 官方商城标签 + 商品双列（设计稿 2 个大卡）
 *
 * 数据源: MallViewModel.officialStoreProducts (从 MallRepository 拿, 合成 mock)
 */
@Composable
private fun OfficialStoreSection(products: List<Product>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color(0xFFE0E0E0)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "官方商城 · 精选",
                color = RedMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color(0xFFE0E0E0)),
            )
        }
        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("加载中…", color = Color(0xFF999999), fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                products.take(2).forEach { p ->
                    ProductCard(
                        title = p.name,
                        price = "¥${kotlin.math.abs(p.name.hashCode()) % 9900 + 100}",
                        soldCount = "已售 ${formatSoldCount(p.name.hashCode())}",
                        imageUrl = p.imageUrl,
                        tag = if (p.name.contains("Pro") || p.name.contains("Ultra")) "旗舰" else "热卖",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (products.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 你可能喜欢 + 2x2 推荐商品
 *
 * 数据源: MallViewModel.recommendProducts (合成 mock, 跨品类)
 */
@Composable
private fun RecommendSection(products: List<Product>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Text("你可能喜欢", color = Color(0xFF333333), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("加载中…", color = Color(0xFF999999), fontSize = 12.sp)
            }
        } else {
            products.take(4).chunked(2).forEach { rowProducts ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowProducts.forEach { p ->
                        ProductCard(
                            title = p.name,
                            price = "¥${kotlin.math.abs(p.name.hashCode()) % 9900 + 100}",
                            soldCount = "已售 ${formatSoldCount(p.name.hashCode())}",
                            imageUrl = p.imageUrl,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowProducts.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun formatSoldCount(seed: Int): String {
    val n = (seed.ushr(1).toLong() and 0xFFFFFF) % 120_000L + 1_000L
    return when {
        n < 10_000 -> n.toString()
        else -> "%.1f万".format(n / 10_000.0)
    }
}

@Composable
private fun ProductCard(
    title: String,
    price: String,
    soldCount: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tag: String? = null,
) {
    val placeholder = rememberImagePlaceholder()
    val errorPainter = rememberImageError()
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
                .clip(RoundedCornerShape(6.dp)),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = errorPainter,
                modifier = Modifier.fillMaxSize(),
            )
            if (tag != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(RedMain)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(tag, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // 设计稿 titleMedium 14sp
        Text(title, color = Color(0xFF333333), fontSize = 14.sp, maxLines = 2)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color(0xFF999999), fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            // 设计稿 price 18sp Bold
            Text(price, color = RedMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        // 设计稿 priceSmall 12sp
        Text(soldCount, color = Color(0xFF999999), fontSize = 12.sp)
    }
}

