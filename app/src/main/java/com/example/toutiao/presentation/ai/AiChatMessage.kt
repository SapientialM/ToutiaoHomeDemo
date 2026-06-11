package com.example.toutiao.presentation.ai

import com.example.toutiao.domain.model.FeedCard

// =============================================================================
// AiChatMessage — 单条聊天消息
//
// 字段说明：
//  - role: USER 用户发送 / ASSISTANT AI 回复
//  - content: 文本内容（已去除工具调用标签，UI 直接渲染）
//  - embeddedNews: 仅 ASSISTANT 消息可挂载，UI 在消息气泡下显示"📰 相关新闻"
//  - isStreaming: 占位字段，为后续流式输出预留（当前每次返回完整响应）
//  - timestamp: 用于排序 & debug
// =============================================================================
data class AiChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val embeddedNews: List<FeedCard> = emptyList(),
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class Role { USER, ASSISTANT }