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
import com.example.toutiao.presentation.profile.MallCoupon
import com.example.toutiao.presentation.profile.SampleCoupons
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.RedMain
import com.example.toutiao.ui.theme.TextHint

// =============================================================================
// CouponsScreen — 券与红包 (商城 5 入口 "券与红包")
// =============================================================================
@Composable
fun CouponsScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = { TopBar(title = "券与红包", onBack = onBack) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            StatsBar(SampleCoupons)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SampleCoupons, key = { it.couponId }) { coupon ->
                    CouponCard(coupon)
                }
            }
        }
    }
}

@Composable
private fun StatsBar(coupons: List<MallCoupon>) {
    val unused = coupons.count { it.status == "未使用" }
    val totalAmount = coupons.filter { it.status == "未使用" }.sumOf { it.amount }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatColumn(value = "${unused}", label = "未使用")
        Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color(0xFFEEEEEE)))
        StatColumn(value = "¥${totalAmount}", label = "总额度")
        Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color(0xFFEEEEEE)))
        StatColumn(value = "${coupons.count { it.status == "已使用" }}", label = "已使用")
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = RedMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xFF666666), fontSize = 12.sp)
    }
}

@Composable
private fun CouponCard(coupon: MallCoupon) {
    val (enabled, opacity) = when (coupon.status) {
        "未使用" -> true to 1f
        "已使用" -> false to 0.55f
        else -> false to 0.4f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Color(0xFFFFEDED) else Color(0xFFEEEEEE))
            .clickable(enabled = enabled) { /* TODO: 去使用 */ }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧金额
        Box(
            modifier = Modifier
                .width(96.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥", color = RedMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${coupon.amount}", color = RedMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (coupon.minSpend > 0) "满${coupon.minSpend} 可用" else "无门槛",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                )
            }
        }
        // 虚线分割
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(64.dp)
                .background(RedMain.copy(alpha = 0.2f * opacity)),
        )
        Spacer(Modifier.width(12.dp))
        // 右侧详情
        Column(modifier = Modifier.weight(1f)) {
            Text(coupon.title, color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(coupon.expiresAt, color = TextHint, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) RedMain else Color(0xFF999999))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    if (enabled) "立即使用" else coupon.status,
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
