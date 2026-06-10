package com.example.toutiao.presentation.comment

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.data.remote.datasource.Comment
import com.example.toutiao.data.remote.datasource.CommentDataSource
import com.example.toutiao.presentation.common.LocalAppToastHost
import com.example.toutiao.ui.theme.RedMain
import kotlinx.coroutines.flow.collectLatest

// =============================================================================
// CommentSection — 评论列表 + 输入框（视频/新闻详情页共用）
//
// 数据：CommentDataSource（in-memory mock，Hilt @Singleton）
// - 首次进入：种子 5~20 条评论（按 newsId 哈希）
// - 用户提交：调用 dataSource.addComment() → StateFlow 推回列表
// =============================================================================
@Composable
fun CommentSection(
    newsId: String,
    commentDataSource: CommentDataSource,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toast = LocalAppToastHost.current
    val comments by commentDataSource.observe(newsId).collectAsState(initial = commentDataSource.get(newsId))
    var inputText by remember { mutableStateOf("") }
    var inputExpanded by remember { mutableStateOf(false) }

    // 拉取最新（数据源 observe 已支持，但首次默认值需要 get()）
    LaunchedEffect(newsId) {
        // 触发种子化
        commentDataSource.get(newsId)
    }

    Column(modifier = modifier.fillMaxWidth().background(Color.White)) {
        // ── 头部：评论数 + 排序 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "评论 ${comments.size}",
                color = Color(0xFF1A1A1A),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        // ── 分隔线 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFEEEEEE)),
        )

        // ── 评论列表 ──
        if (comments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无评论，快来抢沙发", color = Color(0xFF999999), fontSize = 13.sp)
            }
        } else {
            Column {
                comments.forEach { comment ->
                    CommentRow(
                        comment = comment,
                        onLikeClick = {
                            commentDataSource.likeComment(newsId, comment.id)
                            toast.showSuccess("感谢支持你的精彩评论")
                        },
                    )
                }
            }
        }

        // ── 输入框 ──
        Spacer(Modifier.height(8.dp))
        CommentInputBar(
            input = inputText,
            expanded = inputExpanded,
            onInputChange = { inputText = it },
            onFocusChange = { inputExpanded = it },
            onSend = {
                val text = inputText.trim()
                if (text.isBlank()) {
                    toast.showWarning("写点什么再发布吧")
                } else {
                    commentDataSource.addComment(newsId, text)
                    inputText = ""
                    inputExpanded = false
                    toast.showSuccess("评论发布成功")
                }
            },
        )
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    onLikeClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 头像（彩色圆 + 首字）
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
        // 内容
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.author,
                color = Color(0xFF1A1A1A),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = comment.content,
                color = Color(0xFF333333),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatRelativeTime(comment.timestamp),
                    color = Color(0xFF999999),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.ThumbUp,
                    contentDescription = "点赞",
                    tint = if (comment.likes > 0) RedMain else Color(0xFF999999),
                    modifier = Modifier
                        .size(14.dp)
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

@Composable
private fun CommentInputBar(
    input: String,
    expanded: Boolean,
    onInputChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = Color(0xFF1A1A1A)),
                cursorBrush = SolidColor(RedMain),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text("说点什么…", color = Color(0xFFBBBBBB), fontSize = 14.sp)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        // 发送按钮（输入为空时半透明）
        IconButton(
            onClick = onSend,
            enabled = input.isNotBlank(),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (input.isNotBlank()) RedMain else Color(0xFFE0E0E0)),
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = "发送",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun colorForAuthor(author: String): Color {
    // 简单哈希染色
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