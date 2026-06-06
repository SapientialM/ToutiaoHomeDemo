package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.ui.theme.Divider
import com.example.toutiao.ui.theme.TextPrimary
import com.example.toutiao.ui.theme.TopBadgeRed

@Composable
fun TextTopCard(card: FeedCard.TextTop, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (card.isTop) {
                    Box(
                        modifier = Modifier
                            .background(TopBadgeRed, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("置顶", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = card.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = TextPrimary,
                    lineHeight = 26.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            BottomInfoRow(card.source, card.commentCount, card.publishTime, isTop = card.isTop)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider)
        )
    }
}
