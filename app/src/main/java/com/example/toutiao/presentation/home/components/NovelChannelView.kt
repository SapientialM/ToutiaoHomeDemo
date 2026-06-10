package com.example.toutiao.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.presentation.common.rememberImageError
import com.example.toutiao.presentation.common.rememberImagePlaceholder
import com.example.toutiao.ui.theme.RedMain

/**
 * 小说频道 - 顶部"我的书架"入口栏
 *
 * P1 PM 审查 ISSUE-001 (再次)：PM 第二次审查仍认为"书架混在推荐里造成内容与导航混淆"。
 * 根治方案：把书架从推荐数据中**完全抽离**，作为独立的横条入口，文字+箭头明显是"导航"不是"内容"。
 */
@Composable
fun NovelBookshelfRow(
    books: List<NovelBook>,
    bookshelfCount: Int = 0,
    onBookClick: (String) -> Unit = {},
    onBookshelfClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        // 书架入口 — 独立横条（导航样式），不再与推荐数据混排
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBookshelfClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📚", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "我的书架",
                color = Color(0xFF1A1A1A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (bookshelfCount > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${bookshelfCount} 本",
                    color = Color(0xFF999999),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Text("›", color = Color(0xFFCCCCCC), fontSize = 18.sp)
        }
        // 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFF0F0F0)),
        )
        // 3 本推荐 — 不再混入书架
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            books.forEach { book ->
                NovelBookCard(
                    book = book,
                    onClick = { onBookClick(book.id) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NovelBookCard(
    book: NovelBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholder = rememberImagePlaceholder()
    val errorPainter = rememberImageError()
    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        // PM 审查 ISSUE-001: 加 coverUrl 加载（picsum 确定性占位），失败时降级到 emoji 占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center,
        ) {
            if (book.coverUrl.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    placeholder = placeholder,
                    error = errorPainter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("📖", fontSize = 24.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = book.title,
            color = Color(0xFF1A1A1A),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFFFE8E5))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(
                text = book.tag,
                color = RedMain,
                fontSize = 9.sp,
            )
        }
    }
}

/**
 * 小说频道 - 分区头
 *
 * 设计稿：左 18sp Bold 标题 + 右侧"更多"链接 13sp 灰
 */
@Composable
fun NovelSectionHeader(
    title: String,
    rightText: String? = null,
    onRightClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color(0xFF1A1A1A),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        if (rightText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onRightClick),
            ) {
                Text(
                    text = rightText,
                    color = Color(0xFF999999),
                    fontSize = 13.sp,
                )
                Text(
                    text = " ›",
                    color = Color(0xFF999999),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * 小说频道 - 排行榜子 Tab
 *
 * ISSUE-004: 视觉与一级 Tab 统一：选中=红字加粗 + 红色下划线，未选中=黑字。
 * 不再用红底白字胶囊，与首页一级 Tab 保持同一套语义。
 */
@Composable
fun NovelRankingTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { idx, tab ->
            val selected = idx == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTabSelected(idx) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = tab,
                    color = if (selected) RedMain else Color(0xFF1A1A1A),
                    fontSize = if (selected) 15.sp else 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                // 红色下划线
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(if (selected) 2.dp else 0.dp)
                        .background(RedMain),
                )
            }
        }
    }
}

/**
 * 小说频道 - 排行榜项（设计稿：左侧排名 + 中部封面 + 右侧标题/分类/热度）
 */
@Composable
fun NovelRankItem(
    rank: Int,
    book: NovelBook,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val placeholder = rememberImagePlaceholder()
    val errorPainter = rememberImageError()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // ISSUE-005: 1/2/3 排名颜色按行业惯例（深红/橙/黄），4+ 灰
        val rankColor = when (rank) {
            1 -> Color(0xFFFF3B30)
            2 -> Color(0xFFFFA940)
            3 -> Color(0xFFFAAD14)
            else -> Color(0xFF999999)
        }
        Text(
            text = rank.toString(),
            color = rankColor,
            fontSize = 14.sp,
            fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(20.dp),
        )
        Spacer(Modifier.width(4.dp))
        // ISSUE-003: 统一占位尺寸 48x64（高度与底部对齐），失败/无图都展示灰底 emoji 兜底
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 64.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center,
        ) {
            if (book.coverUrl.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    placeholder = placeholder,
                    error = errorPainter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("📖", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                color = Color(0xFF1A1A1A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            // ISSUE-003: 加作者行（"作者 · 分类"），无作者时只显示分类
            val authorAndCategory = if (book.author.isNotBlank()) {
                "${book.author} · ${book.category}"
            } else {
                book.category
            }
            Text(
                text = listOfNotNull(
                    authorAndCategory.takeIf { it.isNotBlank() },
                    book.heat.takeIf { it.isNotBlank() },
                ).joinToString("  "),
                color = Color(0xFF999999),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 小说频道 - 猜你喜欢大卡
 *
 * 设计稿：左侧 80x110 封面 + 右侧标题/评分/描述/标签
 */
@Composable
fun NovelRecommendItem(
    book: NovelRecommendBook,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 100.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE0E0E0)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.title,
                    color = Color(0xFF1A1A1A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${book.score}分",
                    color = Color(0xFFD4A145),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = book.description,
                color = Color(0xFF999999),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.category,
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(6.dp))
                if (book.status.isNotEmpty()) {
                    Text(
                        text = book.status,
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = book.readers,
                    color = Color(0xFF999999),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

data class NovelBook(
    val id: String,
    val title: String,
    val tag: String = "为你推荐",
    val author: String = "",
    val category: String = "",
    val heat: String = "",
    val coverUrl: String = "",
)

data class NovelRecommendBook(
    val title: String,
    val description: String,
    val category: String,
    val status: String,
    val readers: String,
    val score: String,
)
