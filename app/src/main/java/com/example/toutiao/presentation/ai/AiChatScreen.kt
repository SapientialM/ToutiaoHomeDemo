package com.example.toutiao.presentation.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.presentation.common.LocalAppToastHost
import com.example.toutiao.ui.theme.Background
import com.example.toutiao.ui.theme.CardBackground
import com.example.toutiao.ui.theme.Divider
import com.example.toutiao.ui.theme.RedMain
import com.example.toutiao.ui.theme.TextHint
import com.example.toutiao.ui.theme.TextPrimary
import com.example.toutiao.ui.theme.TextSecondary

// =============================================================================
// AiChatScreen — 豆包 AI 全屏对话页
//
// 布局（自顶向下）：
//  ┌──────────────────────────────┐
//  │ ← 豆包 AI            清除   │  TopAppBar
//  ├──────────────────────────────┤
//  │  [空态：开场白 + 4 个建议]    │
//  │  AI: 你好，有什么可以帮你？   │
//  │       📰 相关新闻 [card][card]│  ← Assistant 消息可挂载新闻
//  │      你: 有什么科技新闻       │
//  │      AI: ...（思考中…）       │
//  ├──────────────────────────────┤
//  │ [输入框]                  [↑] │  BottomInputBar
//  └──────────────────────────────┘
//
// 行为要点：
//  - 新消息自动滚动到底部
//  - AI 思考中：输入框禁用 + 发送按钮禁用 + 消息列表底部显示 loading
//  - 出错：调用 AppToastHost.showError
// =============================================================================
@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    onCardClick: (FeedCard) -> Unit = {},
    viewModel: AiChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val toastHost = LocalAppToastHost.current

    // 错误提示
    LaunchedEffect(state.error) {
        state.error?.let {
            toastHost.showError(it)
            viewModel.dismissError()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AiChatTopBar(
                onBack = onBack,
                onClear = { viewModel.clear() },
                canClear = state.messages.isNotEmpty(),
            )
            ChatMessageList(
                state = state,
                onCardClick = onCardClick,
                onSuggestionClick = { suggestion ->
                    viewModel.onDraftChange(suggestion)
                    viewModel.send()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            BottomInputBar(
                draft = state.draft,
                canSend = state.canSend,
                isResponding = state.isResponding,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send,
            )
        }
    }
}

// ── 顶部栏 ──────────────────────────────────────────────────────────────────

@Composable
private fun AiChatTopBar(
    onBack: () -> Unit,
    onClear: () -> Unit,
    canClear: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.SmartToy,
            contentDescription = null,
            tint = RedMain,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "豆包 AI",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (canClear) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "清除对话",
                    tint = TextSecondary,
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
    HorizontalDivider()
}

// ── 消息列表 ────────────────────────────────────────────────────────────────

@Composable
private fun ChatMessageList(
    state: AiChatUiState,
    onCardClick: (FeedCard) -> Unit,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val showEmpty = state.messages.isEmpty() && !state.isResponding

    // 新消息时自动滚到底部
    LaunchedEffect(state.messages.size, state.isResponding) {
        if (state.messages.isNotEmpty() || state.isResponding) {
            val target = if (state.messages.isEmpty()) 0 else state.messages.lastIndex
            listState.animateScrollToItem(target)
        }
    }

    if (showEmpty) {
        EmptyState(onSuggestionClick = onSuggestionClick, modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier,
            state = listState,
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = state.messages,
                key = { it.id },
            ) { msg ->
                when (msg.role) {
                    Role.USER -> UserBubble(msg)
                    Role.ASSISTANT -> AssistantBubble(
                        msg = msg,
                        onCardClick = onCardClick,
                    )
                }
            }
            if (state.isResponding && state.messages.lastOrNull()?.role != Role.ASSISTANT) {
                item {
                    ThinkingIndicator()
                }
            }
        }
    }
}

@Composable
private fun UserBubble(msg: AiChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = RedMain,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                text = msg.content,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun AssistantBubble(
    msg: AiChatMessage,
    onCardClick: (FeedCard) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(RedMain.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = RedMain,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Divider),
                ) {
                    Text(
                        text = parseSimpleMarkdown(msg.content),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                if (msg.embeddedNews.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    EmbeddedNewsRow(
                        news = msg.embeddedNews,
                        onCardClick = onCardClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(RedMain.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                tint = RedMain,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBackground,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Divider),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                CircularProgressIndicator(
                    color = RedMain,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在思考…",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

// ── 嵌入新闻卡片（横向滚动） ─────────────────────────────────────────────────

@Composable
private fun EmbeddedNewsRow(
    news: List<FeedCard>,
    onCardClick: (FeedCard) -> Unit,
) {
    Column {
        Text(
            text = "📰 相关新闻",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(news, key = { it.id }) { card ->
                EmbeddedNewsCard(card = card, onClick = { onCardClick(card) })
            }
        }
    }
}

@Composable
private fun EmbeddedNewsCard(
    card: FeedCard,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .heightIn(min = 0.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBackground)
            .border(0.5.dp, Divider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        // 缩略图
        val imageUrl = (card as? FeedCard.LeftTextRightImage)?.imageUrl
            ?: (card as? FeedCard.LargeImage)?.imageUrl
            ?: (card as? FeedCard.Video)?.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFEEEEEE)),
            )
            Spacer(Modifier.height(6.dp))
        } else {
            // 无图卡：用纯文字占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "📰",
                    fontSize = 24.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = card.title,
            fontSize = 13.sp,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = card.source.ifBlank { "未知来源" },
            fontSize = 11.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── 空态 ────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = listOf(
        "今天有什么头条新闻",
        "体育赛事速览",
        "AI 最新进展",
        "财经热点",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(RedMain.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                tint = RedMain,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "豆包 AI",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "我是你的新闻智能助手，可以搜索客户端内置新闻库回答你的问题",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "试试这些问题：",
            fontSize = 13.sp,
            color = TextHint,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(start = 4.dp, bottom = 8.dp),
        )
        suggestions.forEach { suggestion ->
            SuggestionChip(text = suggestion, onClick = { onSuggestionClick(suggestion) })
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Divider),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = TextHint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = TextPrimary,
            )
        }
    }
}

// ── 底部输入栏 ──────────────────────────────────────────────────────────────

@Composable
private fun BottomInputBar(
    draft: String,
    canSend: Boolean,
    isResponding: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        color = CardBackground,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = !isResponding,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                singleLine = true,
                cursorBrush = SolidColor(RedMain),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp,
                ),
                decorationBox = { innerTextField ->
                    if (draft.isEmpty()) {
                        Text(
                            text = if (isResponding) "豆包正在思考…" else "说点什么…",
                            color = TextHint,
                            fontSize = 15.sp,
                        )
                    }
                    innerTextField()
                },
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (canSend) RedMain else Color(0xFFE0E0E0))
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── 工具：极简文本高亮（不引入 markdown 库） ─────────────────────────────────

/**
 * 把 LLM 输出的文本做几行轻量渲染：
 *  - **text** → 加粗
 *  - 其他原样
 * 当前 LLM 已按 system prompt 输出纯文本，此函数作为安全网。
 */
private fun parseSimpleMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*")
    var cursor = 0
    regex.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Divider),
    )
}