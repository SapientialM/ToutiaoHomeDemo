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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
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
import coil.compose.AsyncImage
import com.example.toutiao.presentation.common.rememberImageError
import com.example.toutiao.presentation.common.rememberImagePlaceholder
import com.example.toutiao.presentation.profile.MallOrder
import com.example.toutiao.presentation.profile.OrderStatus
import com.example.toutiao.presentation.profile.SampleOrders
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// OrderListScreen — 我的订单 (商城 5 入口 "我的订单")
// =============================================================================
@Composable
fun OrderListScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = { TopBar(title = "我的订单", onBack = onBack) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            StatusFilterRow()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(SampleOrders, key = { it.orderId }) { order ->
                    OrderCard(order)
                }
            }
        }
    }
}

@Composable
private fun StatusFilterRow() {
    val statuses = listOf("全部", "待付款", "待发货", "已发货", "已完成", "退款中")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        statuses.forEachIndexed { idx, label ->
            val selected = idx == 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    color = if (selected) RedMain else Color(0xFF1A1A1A),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (selected) 20.dp else 0.dp)
                        .background(RedMain),
                )
            }
        }
    }
}

@Composable
private fun OrderCard(order: MallOrder) {
    val placeholder = rememberImagePlaceholder()
    val errorPainter = rememberImageError()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { /* TODO: 订单详情 */ }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.orderId, color = Color(0xFF666666), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            StatusBadge(order.status)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = order.productImage,
                contentDescription = null,
                placeholder = placeholder,
                error = errorPainter,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    order.productName,
                    color = Color(0xFF1A1A1A),
                    fontSize = 14.sp,
                    maxLines = 2,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text("x${order.quantity}", color = Color(0xFF999999), fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("¥${order.price}", color = Color(0xFF1A1A1A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.orderTime, color = Color(0xFF999999), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            OrderActionButton(order.status)
        }
    }
}

@Composable
private fun StatusBadge(status: OrderStatus) {
    val (bg, fg) = when (status) {
        OrderStatus.PendingPay -> Color(0xFFFFF1E6) to Color(0xFFFF8C42)
        OrderStatus.PendingShip -> Color(0xFFFFEDED) to RedMain
        OrderStatus.Shipped -> Color(0xFFE6F4FF) to Color(0xFF1B7FE0)
        OrderStatus.Completed -> Color(0xFFE0E0E0) to Color(0xFF666666)
        OrderStatus.Refunding -> Color(0xFFFFE6E6) to Color(0xFFFF4D4F)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(status.label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OrderActionButton(status: OrderStatus) {
    val (label, color) = when (status) {
        OrderStatus.PendingPay -> "去付款" to RedMain
        OrderStatus.PendingShip -> "提醒发货" to Color(0xFF1B7FE0)
        OrderStatus.Shipped -> "查看物流" to Color(0xFF1B7FE0)
        OrderStatus.Completed -> "再次购买" to Color(0xFF1A1A1A)
        OrderStatus.Refunding -> "退款详情" to Color(0xFFFF4D4F)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable { /* TODO: 动作 */ }
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, rightAction: (@Composable () -> Unit)? = null) {
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
        Text(title, color = Color(0xFF1A1A1A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        rightAction?.invoke()
    }
}
