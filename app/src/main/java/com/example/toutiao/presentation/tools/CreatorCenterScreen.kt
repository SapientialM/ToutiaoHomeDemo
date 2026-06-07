package com.example.toutiao.presentation.tools

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
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
// CreatorCenterScreen — 创作中心 (我的页 4 function card 之一)
// =============================================================================
@Composable
fun CreatorCenterScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = { TopBar(title = "创作中心", onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { StatCards() }
            item { Spacer(Modifier.height(4.dp)) }
            item { QuickActionsCard() }
            item { Spacer(Modifier.height(4.dp)) }
            items(CreatorTools, key = { it.label }) { tool ->
                ToolRow(tool)
            }
        }
    }
}

@Composable
private fun StatCards() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            icon = Icons.Filled.Edit,
            value = "32",
            label = "已发布",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.ThumbUp,
            value = "1.2w",
            label = "总点赞",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.Star,
            value = "856",
            label = "新增粉丝",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = RedMain, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color(0xFF666666), fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color(0xFF1A1A1A), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickActionsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Text("快速发布", color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("📝", "发微头条", modifier = Modifier.weight(1f))
            ActionButton("📷", "发图集", modifier = Modifier.weight(1f))
            ActionButton("🎬", "发视频", modifier = Modifier.weight(1f))
            ActionButton("🔴", "开直播", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionButton(emoji: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFEDED))
            .clickable { /* TODO: 发布 */ }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = RedMain, fontSize = 11.sp)
    }
}

data class CreatorTool(
    val icon: ImageVector,
    val label: String,
    val subtitle: String,
)

val CreatorTools: List<CreatorTool> = listOf(
    CreatorTool(Icons.Filled.Edit, "内容管理", "管理已发布的微头条 / 文章 / 视频"),
    CreatorTool(Icons.Filled.LocalFireDepartment, "热门活动", "618 / 暑期 / 国庆 创作激励"),
    CreatorTool(Icons.Filled.Star, "创作者权益", "原创保护 · 流量扶持 · 收益分成"),
)

@Composable
private fun ToolRow(tool: CreatorTool) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { /* TODO: 跳转 */ }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFFFEDED)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tool.icon, contentDescription = null, tint = RedMain, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tool.label, color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(tool.subtitle, color = Color(0xFF999999), fontSize = 12.sp)
        }
    }
}
