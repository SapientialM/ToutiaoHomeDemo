package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.RedMain

/**
 * 财经频道 - 风险提示条
 *
 * 设计稿：浅灰 #F5F5F5 底 + 橙色感叹号图标 + 居中文字
 */
@Composable
fun FinanceRiskNotice(
    text: String = "投资有风险，选择及购买需谨慎",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFFF8800),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                color = Color(0xFF666666),
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * 财经频道 - 股票指数卡
 *
 * 设计稿：白底圆角 8dp，三等分横向展示三大指数（上证/深证/创业板），
 * 每列：名称 + 当前点位 + 涨跌幅；底部数据来源 + 更新时间。
 */
@Composable
fun FinanceStockIndexCard(
    indices: List<StockIndex>,
    dataSource: String = "华泰证券-提供数据支持",
    updateAt: String = "更新于 2026-06-07 10:07",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            indices.forEach { index ->
                StockIndexColumn(
                    index = index,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$dataSource  $updateAt",
            color = Color(0xFFB5B5B5),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun StockIndexColumn(
    index: StockIndex,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 设计稿：指数名称 14sp Regular
        Text(
            text = index.name,
            color = Color(0xFF1A1A1A),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(4.dp))
        // 设计稿：stockValue 22sp Bold
        Text(
            text = index.value,
            color = index.color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        // 设计稿：stockChange 12sp
        Text(
            text = "${index.change}  ${index.changePercent}",
            color = Color(0xFF999999),
            fontSize = 12.sp,
        )
    }
}

data class StockIndex(
    val name: String,
    val value: String,
    val change: String,
    val changePercent: String,
    val color: Color = Color(0xFF00B96B),
)
