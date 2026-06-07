package com.example.toutiao.presentation.wallet

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.presentation.mall.sub.TopBar
import com.example.toutiao.presentation.profile.WalletItem
import com.example.toutiao.presentation.profile.WalletItems
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// WalletScreen — 我的钱包 (我的页 钱包区进入)
//
// 结构:
//   1. 顶部红色渐变收入卡 (金币 + 现金 + 收益汇总)
//   2. 6 格功能区 (金币/券/现金/订单/收藏/历史, 复用 ProfileMockData.WalletItems)
//   3. 8 条 收益明细 (按日聚合的合成记录)
// =============================================================================
@Composable
fun WalletScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = { TopBar(title = "我的钱包", onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { IncomeCard() }
            item { Spacer(Modifier.height(8.dp)) }
            item { WalletGrid() }
            item { Spacer(Modifier.height(8.dp)) }
            item { IncomeDetail() }
        }
    }
}

@Composable
private fun IncomeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53)),
                ),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("累计收益 (元)", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥", color = Color.White, fontSize = 18.sp)
                    Text("186.02", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("金币余额 18,602", color = Color.White.copy(alpha = 0.95f), fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    ) {
                        Text("提现", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("💰", fontSize = 30.sp)
            }
        }
    }
}

@Composable
private fun WalletGrid() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        WalletItems.chunked(3).forEachIndexed { rowIdx, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rowItems.forEach { item ->
                    WalletGridItem(item, modifier = Modifier.weight(1f))
                }
            }
            if (rowIdx < WalletItems.chunked(3).lastIndex) {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun WalletGridItem(item: WalletItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable { /* TODO: 跳转 */ },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
            .background(Color(0xFFFFF1E6)),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.icon, fontSize = 22.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(item.label, color = Color(0xFF666666), fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(item.value, color = Color(0xFF1A1A1A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

data class IncomeRecord(
    val date: String,
    val source: String, // 看新闻/看视频/签到/...
    val amount: Int,    // 金币数
    val cashYuan: String, // 对应人民币 (留空 = 不可提现)
)

val SampleIncomeRecords: List<IncomeRecord> = listOf(
    IncomeRecord("今天", "看新闻", 320, "0.32"),
    IncomeRecord("今天", "签到", 40, "0.04"),
    IncomeRecord("今天", "看视频", 80, "0.08"),
    IncomeRecord("昨天", "看新闻", 280, "0.28"),
    IncomeRecord("昨天", "看视频", 60, "0.06"),
    IncomeRecord("6月5日", "看新闻", 350, "0.35"),
    IncomeRecord("6月5日", "看广告", 50, "0.05"),
    IncomeRecord("6月4日", "看新闻", 290, "0.29"),
)

@Composable
private fun IncomeDetail() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("收益明细", color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("近 7 天", color = Color(0xFF999999), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        SampleIncomeRecords.forEach { record ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(record.source, color = Color(0xFF1A1A1A), fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("+${record.amount} 金币", color = RedMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text("¥${record.cashYuan}", color = Color(0xFF999999), fontSize = 11.sp)
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(0xFFEEEEEE)),
            )
        }
    }
}
