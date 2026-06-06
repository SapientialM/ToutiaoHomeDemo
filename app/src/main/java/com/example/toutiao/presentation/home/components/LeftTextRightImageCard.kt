package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.ui.theme.Divider
import com.example.toutiao.ui.theme.TextPrimary

@Composable
fun LeftTextRightImageCard(card: FeedCard.LeftTextRightImage, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = TextPrimary,
                    lineHeight = 24.sp,
                )
                Spacer(Modifier.height(10.dp))
                BottomInfoRow(card.source, card.commentCount, card.publishTime, isTop = card.isTop)
            }
            Spacer(Modifier.width(12.dp))
            AsyncImage(
                model = card.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color(0xFFF0F0F0)),
                error = ColorPainter(Color(0xFFE0E0E0)),
                modifier = Modifier
                    .size(width = 116.dp, height = 78.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider)
        )
    }
}
