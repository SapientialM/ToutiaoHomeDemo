package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.TextSecondary
import com.example.toutiao.ui.theme.TopBadgeRed

@Composable
fun BottomInfoRow(source: String, commentCount: Int, publishTime: String, isTop: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isTop) {
            Text(
                text = "置顶", 
                fontSize = 12.sp, 
                color = TopBadgeRed,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = source, 
            fontSize = 12.sp, 
            color = TextSecondary
        )
        if (commentCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${formatCount(commentCount)}评论",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        if (publishTime.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = publishTime,
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
    }
}

private fun formatCount(count: Int): String = when {
    count >= 10000 -> "${count / 10000}万"
    count >= 1000 -> "${count / 1000}千"
    else -> count.toString()
}
