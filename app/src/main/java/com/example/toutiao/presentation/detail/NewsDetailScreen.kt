package com.example.toutiao.presentation.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.toutiao.domain.model.NewsContent
import com.example.toutiao.domain.model.NewsParagraph
import com.example.toutiao.domain.repository.NewsContentRepository
import com.example.toutiao.presentation.common.rememberImageError
import com.example.toutiao.presentation.common.rememberImagePlaceholder
import com.example.toutiao.ui.theme.RedMain

// =============================================================================
// NewsDetailScreen — 新闻详情页
//
// 设计要点：
//  1. 状态机驱动：NewsDetailViewModel 暴露 Idle / Loading(stage) / ContentReady / Error
//  2. Loading 阶段显示「正在做什么」的中间态（fetching / manual / LLM）
//  3. ContentReady 阶段渲染标题 + 来源 + 段落 + 配图 + 操作栏
//  4. 错误态：可重试
// =============================================================================

@Composable
fun NewsDetailScreen(
    sourceUrl: String,
    fallbackTitle: String?,
    onBack: () -> Unit,
    viewModel: NewsDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // sourceUrl 变化时重新加载
    LaunchedEffect(sourceUrl) {
        if (sourceUrl.isNotBlank()) {
            viewModel.load(sourceUrl, fallbackTitle)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        when (val stage = uiState.detailStage) {
            is NewsDetailStage.Idle -> Unit
            is NewsDetailStage.Loading -> LoadingState(stage = stage.repoStage)
            is NewsDetailStage.ContentReady -> ContentState(
                content = stage.content,
                byLlm = stage.byLlm,
                onBack = onBack,
            )
            is NewsDetailStage.Error -> ErrorState(
                message = stage.message,
                onRetry = viewModel::retry,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun LoadingState(stage: NewsContentRepository.Stage) {
    val (label, sublabel) = when (stage) {
        is NewsContentRepository.Stage.Fetching -> "正在拉取网页" to "HTTP 访问源 URL…"
        is NewsContentRepository.Stage.ManualParsing -> "正在手动解析" to "Jsoup 提取标题 / 段落 / 配图…"
        is NewsContentRepository.Stage.LlmParsing -> "智能解析中" to "手动解析内容不足，回退到 LLM…"
        is NewsContentRepository.Stage.MockParsing -> "生成示例内容" to "Jsoup + LLM 暂不可用，使用占位内容…"
        else -> "加载中" to ""
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = RedMain, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(label, color = Color(0xFF1A1A1A), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            sublabel,
            color = Color(0xFF999999),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ContentState(
    content: NewsContent,
    byLlm: Boolean,
    onBack: () -> Unit,
) {
    val placeholder = rememberImagePlaceholder()
    val errorPainter = rememberImageError()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(onBack = onBack, byLlm = byLlm)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // 标题 + 头图
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = content.title,
                        color = Color(0xFF1A1A1A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = content.source,
                            color = Color(0xFF666666),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = content.publishTime,
                            color = Color(0xFF999999),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            content.coverUrl?.takeIf { it.isNotBlank() }?.let { coverUrl ->
                item {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = placeholder,
                        error = errorPainter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(220.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            // 段落
            items(content.paragraphs) { paragraph ->
                ParagraphView(paragraph = paragraph, placeholder = placeholder, errorPainter = errorPainter)
            }
            // 源链接
            item {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "源链接：",
                        color = Color(0xFF999999),
                        fontSize = 11.sp,
                    )
                    Text(
                        text = content.sourceUrl,
                        color = Color(0xFF4A90E2),
                        fontSize = 11.sp,
                        maxLines = 2,
                    )
                }
                BottomActionBar()
            }
        }
    }
}

@Composable
private fun ParagraphView(
    paragraph: NewsParagraph,
    placeholder: androidx.compose.ui.graphics.painter.Painter,
    errorPainter: androidx.compose.ui.graphics.painter.Painter,
) {
    when (paragraph) {
        is NewsParagraph.Text -> {
            Text(
                text = paragraph.text,
                color = Color(0xFF1A1A1A),
                fontSize = 16.sp,
                lineHeight = 26.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        is NewsParagraph.Image -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AsyncImage(
                    model = paragraph.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = errorPainter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                paragraph.caption?.let { caption ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = caption,
                        color = Color(0xFF999999),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        is NewsParagraph.Quote -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(Color(0xFFF5F5F5))
                    .padding(12.dp),
            ) {
                Text(
                    text = "「 ${paragraph.text} 」",
                    color = Color(0xFF666666),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                )
            }
        }
        is NewsParagraph.Video -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "播放视频",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit, byLlm: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color(0xFF1A1A1A),
            )
        }
        Spacer(Modifier.weight(1f))
        if (byLlm) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF1F0))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = RedMain,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "LLM 解析",
                    color = RedMain,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = { /* TODO: 分享 */ }, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "分享",
                tint = Color(0xFF1A1A1A),
            )
        }
        IconButton(onClick = { /* TODO: 更多 */ }, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "更多",
                tint = Color(0xFF1A1A1A),
            )
        }
    }
}

@Composable
private fun BottomActionBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("写评论…", color = Color(0xFF999999), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.ChatBubble,
            contentDescription = "评论",
            tint = Color(0xFF666666),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "点赞",
            tint = Color(0xFF666666),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "分享",
            tint = Color(0xFF666666),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            tint = Color(0xFFFF5757),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "加载失败",
            color = Color(0xFF1A1A1A),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = Color(0xFF666666),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF5F5F5))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("返回", color = Color(0xFF666666), fontSize = 14.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(RedMain)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("重试", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}
