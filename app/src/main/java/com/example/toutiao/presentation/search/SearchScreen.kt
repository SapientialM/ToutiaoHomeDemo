package com.example.toutiao.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.presentation.common.FeedCardItem
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// SearchScreen — 搜索页（对接真实Mock数据）
// =============================================================================
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    onCardClick: (FeedCard) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasError by viewModel.hasError.collectAsState()

    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SearchPageTopBar(
                query = query,
                onQueryChange = {
                    query = it
                    if (it.isBlank()) {
                        viewModel.clearResults()
                        isSearching = false
                    }
                },
                onSearch = {
                    if (query.isNotBlank()) {
                        isSearching = true
                        viewModel.search(query)
                    }
                },
                onClear = {
                    query = ""
                    viewModel.clearResults()
                    isSearching = false
                },
                onBack = onBack,
            )
        },
        containerColor = Color(0xFFF5F5F5),
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = RedMain)
                }
            }
            hasError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = hasError ?: "搜索失败",
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )
                }
            }
            isSearching && searchResults.isNotEmpty() -> {
                SearchResultList(
                    results = searchResults,
                    onCardClick = onCardClick,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            isSearching && searchResults.isEmpty() && query.isNotBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "未找到\"$query\"相关结果",
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )
                }
            }
            else -> {
                SearchHistoryContent(
                    onHistoryClick = {
                        query = it
                        isSearching = true
                        viewModel.search(it)
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<FeedCard>,
    onCardClick: (FeedCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(results, key = { it.id }) { card ->
            FeedCardItem(
                card = card,
                onClick = { onCardClick(card) },
            )
        }
    }
}

@Composable
private fun SearchPageTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color(0xFF666666),
            )
        }

        Spacer(Modifier.width(4.dp))

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 12.dp),
            singleLine = true,
            cursorBrush = SolidColor(RedMain),
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                color = Color.Black,
                fontSize = 14.sp,
            ),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (query.isEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = Color(0xFFBBBBBB),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "搜索感兴趣的内容",
                            color = Color(0xFFBBBBBB),
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )

        Spacer(Modifier.width(8.dp))

        if (query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "清除",
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Text(
            text = "搜索",
            color = RedMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onSearch() },
        )
    }
}

@Composable
private fun SearchHistoryContent(
    onHistoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hotSearches = listOf(
        "积石山地震", "寒潮预警", "春运", "AI技术", "新能源汽车",
        "乡村振兴", "航天发射", "医保改革", "教育改革", "碳中和",
    )
    val histories = listOf("北京天气", "科技新闻", "体育赛事")

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // 搜索历史
        item {
            Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "搜索历史",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                    )
                    Text(
                        text = "清除",
                        fontSize = 13.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier.clickable { },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    histories.forEach { history ->
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFF5F5F5))
                                .clickable { onHistoryClick(history) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = history,
                                fontSize = 13.sp,
                                color = Color(0xFF666666),
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // 热搜榜
        item {
            Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.TrendingUp,
                        contentDescription = null,
                        tint = RedMain,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "热搜榜",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                    )
                }
                Spacer(Modifier.height(12.dp))
                hotSearches.forEachIndexed { index, keyword ->
                    HotSearchRow(
                        rank = index + 1,
                        keyword = keyword,
                        onClick = { onHistoryClick(keyword) },
                    )
                    if (index < hotSearches.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HotSearchRow(
    rank: Int,
    keyword: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rankColor = when (rank) {
            1 -> RedMain
            2 -> Color(0xFFFF6B35)
            3 -> Color(0xFFFFA500)
            else -> Color(0xFF999999)
        }
        Text(
            text = rank.toString(),
            fontSize = 14.sp,
            fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal,
            color = rankColor,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = keyword,
            fontSize = 14.sp,
            color = Color(0xFF333333),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        SearchScreen()
    }
}
