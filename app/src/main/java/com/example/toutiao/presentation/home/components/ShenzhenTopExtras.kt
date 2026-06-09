package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
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
 * 深圳频道 - 天气条
 *
 * 设计稿：浅灰 #F5F5F5 底；左：温度 + 天气 + 高低温；右：胶囊形"切换城市"按钮
 */
@Composable
fun ShenzhenWeatherStrip(
    temperature: String = "29",
    weather: String = "阴",
    high: String = "30",
    low: String = "26",
    onSwitchCity: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                // MVPTask #6: 天气背景色与天气匹配（阴 → 浅蓝灰）
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFDDE9F5),  // 浅蓝
                        Color(0xFFEAF1F8),  // 微蓝
                    ),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 设计稿：weatherTemp 26sp Medium
            Text(
                text = temperature,
                color = Color(0xFF1A1A1A),
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "°",
                color = Color(0xFF1A1A1A),
                fontSize = 20.sp,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = weather,
                    color = Color(0xFF1A1A1A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$low°/$high°",
                    color = Color(0xFF8A8A8A),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            // MVPTask #6: 切换城市按钮改成半透明（透出天气背景）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .clickable(onClick = onSwitchCity)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = RedMain,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "切换城市",
                        color = Color(0xFF666666),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * 深圳频道 - 本地热榜横条
 *
 * 设计稿：白底 + 红色火焰图标 + 标题 + 右箭头
 */
@Composable
fun ShenzhenLocalHotBanner(
    title: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 红色火焰圆点
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFE8E5)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🔥",
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "深圳热榜：",
                color = RedMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                color = Color(0xFF1A1A1A),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = " ›",
                color = Color(0xFF999999),
                fontSize = 16.sp,
            )
        }
    }
}
