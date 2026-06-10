package com.example.toutiao.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NoFood
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
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
                commentDataSource = viewModel.commentDataSource,
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
    commentDataSource: com.example.toutiao.data.remote.datasource.CommentDataSource,
) {
    val placeholder = rememberImagePlaceholder()
    val errorPainter = rememberImageError()
    var isLiked by remember { mutableStateOf(false) }
    var isFavorited by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current

    val comments by commentDataSource.observe(content.sourceUrl)
        .collectAsState(initial = commentDataSource.get(content.sourceUrl))
    LaunchedEffect(content.sourceUrl) {
        commentDataSource.get(content.sourceUrl)
    }

    // 内容层（LazyColumn + 固定底部 action bar）
    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(onBack = onBack, byLlm = byLlm)
        // LazyColumn.weight(1f) 留出底部空间
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
            }
            // ── 评论 2 标题 ──
            item("comments_header") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "评论 ${comments.size}",
                    color = Color(0xFF1A1A1A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // ── 邀请讨论 / 搜索 ──
            item("invite_search") {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "邀你讨论",
                        color = Color(0xFF1A1A1A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    // 推荐话题卡（高头像 + 标题 + 讨论数 + 去发布）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF6B6B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("高", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "聊聊高考结束后家长寄语尽显…",
                                color = Color(0xFF1A1A1A),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text("6 讨论数", color = Color(0xFF999999), fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFFFE0DC))
                                .clickable { Toast.makeText(context, "已参与", Toast.LENGTH_SHORT).show() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("去发布", color = RedMain, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "搜索",
                        color = Color(0xFF1A1A1A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("高考结束全班大哭", color = Color(0xFF1A1A1A), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Box(modifier = Modifier.width(1.dp).height(14.dp).background(Color(0xFFE0E0E0)))
                        Spacer(Modifier.width(8.dp))
                        Text("老师寄语暖心句子", color = Color(0xFF1A1A1A), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            // ── 评论列表（mock 数据） ──
            items(comments, key = { it.id }) { comment ->
                DetailCommentRow(
                    comment = comment,
                    onLikeClick = {
                        commentDataSource.likeComment(content.sourceUrl, comment.id)
                        Toast.makeText(context, "👍 感谢支持", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            // ── 快捷点评标签 ──
            item("quick_tags") {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("一键发评", "细心答题 👍", "鱼跃龙门 👍", "胜利在望 👍").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF5F5F5))
                                .clickable {
                                    inputText = tag
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(tag, color = Color(0xFF1A1A1A), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        // 固定底部 action bar（始终在屏幕底部，悬浮在 LazyColumn 上方）
        BottomActionBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            onCommentClick = {
                val text = inputText.trim()
                if (text.isBlank()) {
                    Toast.makeText(context, "请先输入评论内容", Toast.LENGTH_SHORT).show()
                } else {
                    commentDataSource.addComment(content.sourceUrl, text)
                    inputText = ""
                    Toast.makeText(context, "发布成功", Toast.LENGTH_SHORT).show()
                }
            },
            onLikeClick = {
                isLiked = !isLiked
                Toast.makeText(
                    context,
                    if (isLiked) "已点赞" else "已取消点赞",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onShareClick = { Toast.makeText(context, "分享功能开发中", Toast.LENGTH_SHORT).show() },
            onFavoriteClick = {
                isFavorited = !isFavorited
                Toast.makeText(
                    context,
                    if (isFavorited) "已收藏" else "已取消收藏",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
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
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
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
        // 分享按钮：弹 toast 模拟分享
        IconButton(
            onClick = { Toast.makeText(context, "分享功能开发中", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "分享",
                tint = Color(0xFF1A1A1A),
            )
        }
        Box {
            // 更多按钮：下拉菜单
            IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "更多",
                    tint = Color(0xFF1A1A1A),
                )
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("收藏") },
                    leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
                    onClick = {
                        showMoreMenu = false
                        Toast.makeText(context, "已收藏", Toast.LENGTH_SHORT).show()
                    },
                )
                DropdownMenuItem(
                    text = { Text("复制链接") },
                    leadingIcon = { Icon(Icons.Outlined.Link, null) },
                    onClick = {
                        showMoreMenu = false
                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                    },
                )
                DropdownMenuItem(
                    text = { Text("不感兴趣") },
                    leadingIcon = { Icon(Icons.Outlined.NoFood, null) },
                    onClick = {
                        showMoreMenu = false
                        Toast.makeText(context, "已减少此类推荐", Toast.LENGTH_SHORT).show()
                    },
                )
                DropdownMenuItem(
                    text = { Text("屏蔽作者") },
                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                    onClick = {
                        showMoreMenu = false
                        Toast.makeText(context, "已屏蔽该作者", Toast.LENGTH_SHORT).show()
                    },
                )
                DropdownMenuItem(
                    text = { Text("举报") },
                    leadingIcon = { Icon(Icons.Outlined.Report, null) },
                    onClick = {
                        showMoreMenu = false
                        Toast.makeText(context, "举报已提交", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onCommentClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFEEEEEE)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 「说点什么…」输入框
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Color(0xFF1A1A1A)),
                    cursorBrush = SolidColor(RedMain),
                    decorationBox = { inner ->
                        if (inputText.isEmpty()) {
                            Text("看了这么多，说点什么吧", color = Color(0xFF999999), fontSize = 14.sp)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            // 发布按钮（输入为空时半透明）
            IconButton(
                onClick = onCommentClick,
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (inputText.isNotBlank()) RedMain else Color(0xFFE0E0E0))
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "发布",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        // 第二行：4 个图标（分享 / 评论数 / 点赞 / 收藏）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BottomIconLabel(icon = Icons.Filled.Share, label = "分享", onClick = onShareClick)
            BottomIconLabel(icon = Icons.Filled.ChatBubble, label = "评论", onClick = onCommentClick)
            BottomIconLabel(icon = Icons.Filled.Favorite, label = "点赞", onClick = onLikeClick)
            BottomIconLabel(icon = Icons.Filled.Bookmark, label = "收藏", onClick = onFavoriteClick)
        }
    }
}

@Composable
private fun BottomIconLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF666666),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color(0xFF666666), fontSize = 12.sp)
    }
}

// ── 已移除 CommentListDialog / CommentRow / CommentInputBar ──
// 评论列表内嵌 LazyColumn，输入框合并到 BottomActionBar（悬浮底部）

/**
 * 详情页评论列表行（仿设计稿）
 * - 左侧首字彩色头像
 * - 右侧作者 + 时间 + 内容 + 回复/点赞图标
 */
@Composable
private fun DetailCommentRow(
    comment: com.example.toutiao.data.remote.datasource.Comment,
    onLikeClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colorForAuthor(comment.author)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = comment.author.take(1),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    color = Color(0xFF1A1A1A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(comment.timestamp),
                    color = Color(0xFF999999),
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = comment.content,
                color = Color(0xFF333333),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("回复", color = Color(0xFF666666), fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.ThumbUp,
                    contentDescription = "点赞",
                    tint = if (comment.likes > 0) RedMain else Color(0xFF999999),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onLikeClick),
                )
                if (comment.likes > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = comment.likes.toString(),
                        color = Color(0xFF999999),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private fun colorForAuthor(author: String): Color {
    val palette = listOf(
        Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFA94D), Color(0xFF5C7CFA),
        Color(0xFF845EC2), Color(0xFFFF6F91), Color(0xFF00C9A7), Color(0xFFFFC75F),
    )
    val idx = (author.hashCode().toLong() and 0x7FFFFFFF).toInt() % palette.size
    return palette[idx]
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return "刚刚"
    val mins = diff / 60_000L
    return when {
        mins < 1 -> "刚刚"
        mins < 60 -> "${mins}分钟前"
        mins < 60 * 24 -> "${mins / 60}小时前"
        mins < 60 * 24 * 30 -> "${mins / (60 * 24)}天前"
        else -> "${mins / (60 * 24 * 30)}个月前"
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // 错误 toast 提示
    LaunchedEffect(message) {
        Toast.makeText(context, "内容加载失败：$message", Toast.LENGTH_LONG).show()
    }
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
