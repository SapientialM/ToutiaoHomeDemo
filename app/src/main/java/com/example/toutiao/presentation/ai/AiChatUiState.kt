package com.example.toutiao.presentation.ai

// =============================================================================
// AiChatUiState — AI 对话页 UI 状态
//
// 设计要点：
//  - messages: 消息列表（最新在末尾，符合聊天习惯）
//  - draft: 输入框当前草稿（Compose state 由 VM 持有，便于 isResponding 期间禁用输入）
//  - isResponding: LLM 思考中，UI 显示"正在思考…"，发送按钮禁用
//  - error: 全局错误（API key 缺失 / 网络异常 / 工具调用失败等）
// =============================================================================
data class AiChatUiState(
    val messages: List<AiChatMessage> = emptyList(),
    val draft: String = "",
    val isResponding: Boolean = false,
    val error: String? = null,
) {
    val canSend: Boolean
        get() = draft.isNotBlank() && !isResponding
}