package com.example.toutiao.presentation.mall.sub

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import com.example.toutiao.presentation.common.rememberImageError
import com.example.toutiao.presentation.common.rememberImagePlaceholder
import com.example.toutiao.presentation.profile.FollowedShop
import com.example.toutiao.presentation.profile.SampleFollowedShops
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// FollowedShopsScreen — 关注店铺 (商城 5 入口 "关注店铺")
// =============================================================================
@Composable
fun FollowedShopsScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = { TopBar(title = "关注店铺", onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(SampleFollowedShops, key = { it.shopId }) { shop ->
                ShopRow(shop)
            }
        }
    }
}

@Composable
private fun ShopRow(shop: FollowedShop) {
    val placeholder = rememberImagePlaceholder()
    val errorPainter = rememberImageError()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { /* TODO: 进店 */ }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = shop.shopLogo,
                contentDescription = null,
                placeholder = placeholder,
                error = errorPainter,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    shop.shopName,
                    color = Color(0xFF1A1A1A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (shop.newArrivalCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RedMain)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text("${shop.newArrivalCount} 件上新", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "新品: ${shop.latestProductName} ¥${shop.latestProductPrice}",
                color = Color(0xFF666666),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(RedMain.copy(alpha = 0.1f))
                .clickable { /* TODO: 进店 */ }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text("进店", color = RedMain, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
