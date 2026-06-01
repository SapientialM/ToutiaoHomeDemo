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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun LeftTextRightImageCard(card: FeedCard.LeftTextRightImage, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column {
            Box(modifier = Modifier.padding(if (card.isTop) 8.dp else 12.dp)) {
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(if (card.isTop) 4.dp else 8.dp))
                        BottomInfoRow(card.source, card.commentCount, card.publishTime, isTop = card.isTop)
                    }
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(Color(0xFFEEEEEE)),
                        error = ColorPainter(Color(0xFFDDDDDD)),
                        modifier = Modifier
                            .size(width = 160.dp, height = 106.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(0xFFEEEEEE))
            )
        }
    }
}
