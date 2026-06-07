package com.example.toutiao.presentation.earn

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// EarnScreen — "赚钱"页（仿头条极速版 任务中心）
//
// 设计参照：design/赚钱页面.jpg
// 核心区域：
//   1. 顶部白色标题栏（"做任务领金币" + 关闭按钮）
//   2. 红橙渐变收入卡（现金收益 0 元 + 金币）
//   3. 4 个任务入口（走路/吃饭/睡觉/看广告）
//   4. 任务列表（看头条赚金币 / 天天领金币 / 限时预约 / 看广告赚 / 看短剧赚）
//   5. 右下角悬浮"开宝箱得金币"按钮
// =============================================================================
@Composable
fun EarnScreen() {
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
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item { EarnTopBar() }
                item { Spacer(Modifier.height(8.dp)) }
                item { IncomeCard() }
                item { Spacer(Modifier.height(12.dp)) }
                item { TaskQuickEntries() }
                item { Spacer(Modifier.height(12.dp)) }
                item { TaskList() }
            }

            TreasureBoxFloating(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun EarnTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = "做任务领金币",
            color = Color(0xFF1A1A1A),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF5F5F5))
                .clickable { /* TODO: 关闭 */ }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("关闭本页", color = Color(0xFF666666), fontSize = 12.sp)
        }
    }
}

@Composable
private fun IncomeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFF7B5C), Color(0xFFFF5757)),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("现金收益", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("0", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("元", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFC83A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("¥", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("金币 0", color = Color.White, fontSize = 12.sp)
                    Text("  |  ", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text("活动收益", color = Color.White, fontSize = 12.sp)
                    Text(" >", color = Color.White, fontSize = 12.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+138", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("看广告赚钱", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun TaskQuickEntries() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QuickEntry("走路赚金币", Icons.Filled.DirectionsWalk)
            QuickEntry("吃饭赚金币", Icons.Filled.Restaurant)
            QuickEntry("睡觉赚金币", Icons.Filled.Bedtime)
            QuickEntry("看广告赚钱", Icons.Filled.PhoneAndroid)
        }
    }
}

@Composable
private fun QuickEntry(label: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { /* TODO: 跳转任务详情 */ },
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFEDED)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = RedMain, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFF333333), fontSize = 12.sp)
    }
}

@Composable
private fun TaskList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
    ) {
        TaskRow(
            title = "看头条赚金币",
            subtitle = "已赞 4 金币，再看 2 分钟可领",
            buttonText = "去翻倍",
            buttonColor = RedMain,
        )
        DividerLine()
        TaskRow(
            title = "天天领金币",
            subtitle = "今日签到立即领 500 金币，做任务最高领 36...",
            buttonText = "去领取",
            reward = "最高368578",
            buttonColor = RedMain,
        )
        DividerLine()
        TaskRow(
            title = "限时预约领金币",
            subtitle = "今日预约，明日额外获得 8000 金币",
            buttonText = "去预约",
            reward = "12000金币",
            buttonColor = RedMain,
        )
        DividerLine()
        TaskRow(
            title = "看广告赚钱",
            subtitle = "每天可完成 10 次，已完成 0/10 次",
            buttonText = "去完成",
            reward = "138金币",
            buttonColor = RedMain,
        )
        DividerLine()
        TaskRow(
            title = "看短剧赚金币",
            subtitle = "今日看短剧最高赚 1072 金币",
            buttonText = "去看剧",
            reward = "1072金币",
            buttonColor = RedMain,
        )
    }
}

@Composable
private fun TaskRow(
    title: String,
    subtitle: String,
    buttonText: String,
    reward: String? = null,
    buttonColor: Color = RedMain,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (reward != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF4E5))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("¥ $reward", color = Color(0xFFFF8800), fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = Color(0xFF999999),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(buttonColor)
                .clickable { /* TODO: 跳转任务 */ }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(buttonText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(Color(0xFFEEEEEE)),
    )
}

@Composable
private fun TreasureBoxFloating(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500)),
                    ),
                )
                .clickable { /* TODO: 开宝箱 */ },
            contentAlignment = Alignment.Center,
        ) {
            Text("🎁", fontSize = 28.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text("开宝箱得金币", color = Color(0xFFFF5757), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
