package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomInfoRow(source: String, commentCount: Int, publishTime: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Text(text = source, fontSize = 12.sp, color = Color.Gray)
        if (commentCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${formatCount(commentCount)}评论",
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }
    }
}

private fun formatCount(count: Int): String = when {
    count >= 10000 -> "${count / 10000}万"
    count >= 1000 -> "${count / 1000}千"
    else -> count.toString()
}
