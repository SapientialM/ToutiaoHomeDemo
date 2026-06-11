package com.example.toutiao.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toutiao.data.llm.ChatResult
import com.example.toutiao.data.llm.LlmMessage
import com.example.toutiao.data.llm.MinimaxChatClient
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

// =============================================================================
// AiChatViewModel — 豆包 AI 对话状态机
//
// 工作流（用户发消息 → AI 回复）：
//  1. 推入 USER 消息，清空 draft，isResponding=true
//  2. 第一次 LLM 调用：system + 全部历史 messages + 新 USER message
//  3. 解析响应：
//     - 若含 <SEARCH>...</SEARCH>：提取 query → 调 newsRepository.searchNews(query)
//       → 构造 tool result message → 第二次 LLM 调用 → 解析最终响应
//     - 若无标签：直接作为最终回复
//  4. 剥离最终响应中的 <SEARCH> 残留标签（防御性） → 推入 ASSISTANT 消息
//  5. isResponding=false
//
// 工具调用次数上限 1 次/轮（与 system prompt 一致），防止死循环。
// 异常：API key 缺失/网络失败 → UI 状态设 error，UI 显示 Toast。
// =============================================================================
@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val chatClient: MinimaxChatClient,
    private val newsRepository: NewsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AiChatUiState())
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isBlank() || _state.value.isResponding) return

        val userMsg = AiChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            content = text,
        )
        _state.update {
            it.copy(
                messages = it.messages + userMsg,
                draft = "",
                isResponding = true,
                error = null,
            )
        }
        runAgentTurn(userMsg)
    }

    fun clear() {
        _state.update { AiChatUiState() }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * 跑一轮对话：第一次 LLM → 检测 tool → 第二次 LLM → 写回 ASSISTANT 消息。
     * 此函数假设 state 中已包含最新的 USER 消息。
     */
    private fun runAgentTurn(userMsg: AiChatMessage) {
        viewModelScope.launch {
            try {
                // ── Round 1: 第一次 LLM 调用 ──
                val firstResult = chatClient.chat(
                    messages = listOf(LlmMessage(role = "user", content = userMsg.content)),
                    systemPrompt = AI_SYSTEM_PROMPT,
                    temperature = 0.5,
                )

                if (firstResult is ChatResult.Failure) {
                    Timber.w("AiChatVM — round1 failed: ${firstResult.reason}")
                    _state.update {
                        it.copy(
                            isResponding = false,
                            error = "AI 暂不可用：${firstResult.reason}",
                        )
                    }
                    return@launch
                }

                val firstContent = (firstResult as ChatResult.Success).content
                val matchResult = MinimaxChatClient.SEARCH_TAG_REGEX.find(firstContent)

                // ── Round 2 (optional): 工具调用 ──
                if (matchResult != null) {
                    val query = matchResult.groupValues[1].trim()
                    Timber.d("AiChatVM — tool call detected, query='$query'")

                    if (query.isBlank()) {
                        // 标签为空 → 当作普通回复
                        pushAssistantMessage(stripSearchTags(firstContent), emptyList())
                        return@launch
                    }

                    val news = try {
                        newsRepository.searchNews(query).take(8)
                    } catch (e: Exception) {
                        Timber.w(e, "AiChatVM — search tool failed for query='$query'")
                        emptyList()
                    }
                    Timber.d("AiChatVM — search returned ${news.size} items")

                    // 构造 tool result message（喂给 LLM 的精简摘要，避免塞太长的 content）
                    val toolResultText = if (news.isEmpty()) {
                        "搜索结果为空，没有找到关于「$query」的新闻。"
                    } else {
                        buildString {
                            append("搜索到 ${news.size} 条关于「$query」的新闻：\n")
                            news.forEachIndexed { index, card ->
                                append("${index + 1}. ${card.title}")
                                if (card.source.isNotBlank()) append("（来源：${card.source}）")
                                append("\n")
                            }
                        }
                    }

                    val secondResult = chatClient.chat(
                        messages = listOf(
                            LlmMessage(role = "user", content = userMsg.content),
                            LlmMessage(role = "assistant", content = firstContent),
                            LlmMessage(role = "user", content = "工具结果：\n$toolResultText\n\n请基于以上搜索结果给用户最终回答。"),
                        ),
                        systemPrompt = AI_SYSTEM_PROMPT,
                        temperature = 0.5,
                    )

                    if (secondResult is ChatResult.Failure) {
                        Timber.w("AiChatVM — round2 failed: ${secondResult.reason}")
                        // 即便第二次失败，也用第一次响应 + 工具结果拼一个降级回复
                        pushAssistantMessage(
                            "我帮你搜到了 ${news.size} 条相关新闻（见下方卡片）。完整回答获取失败：${secondResult.reason}",
                            news,
                        )
                        return@launch
                    }

                    val finalContent = (secondResult as ChatResult.Success).content
                    pushAssistantMessage(stripSearchTags(finalContent), news)
                } else {
                    // ── 无工具调用：直接展示第一次响应 ──
                    pushAssistantMessage(stripSearchTags(firstContent), emptyList())
                }
            } catch (e: Exception) {
                Timber.e(e, "AiChatVM — unexpected error")
                _state.update {
                    it.copy(
                        isResponding = false,
                        error = "出错了：${e.message ?: e.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    private fun pushAssistantMessage(content: String, news: List<FeedCard>) {
        _state.update {
            it.copy(
                messages = it.messages + AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = Role.ASSISTANT,
                    content = content.ifBlank { "（AI 返回了空内容）" },
                    embeddedNews = news,
                ),
                isResponding = false,
            )
        }
    }

    /**
     * 防御性剥离残留的 <SEARCH>...</SEARCH> 标签（含未配对或多余标签），
     * 避免脏数据进 UI。
     */
    private fun stripSearchTags(content: String): String {
        return content
            .replace(MinimaxChatClient.SEARCH_TAG_REGEX, "")
            .replace(Regex("<SEARCH>|</SEARCH>"), "")
            .trim()
    }
}