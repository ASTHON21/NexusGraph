package com.example.knowledgegraph.model

/**
 * 核心知识节点模型（节点即文档，文档即节点，归属于特定课题画板空间）
 */
data class NodeData(
    val id: String,
    val title: String,
    val markdownContent: String,
    val tagList: List<String>,
    val color: String,
    val x: Float,
    val y: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val boardId: String = BoardTopic.BOARD_METHODOLOGY
) {
    /**
     * 提取用于卡片展示的纯文本摘要
     */
    val summarySnippet: String
        get() = markdownContent
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("```") && !it.startsWith("---") }
            .joinToString(" ")
            .replace(Regex("[*_`~>\\[\\]]"), "")
            .take(120)
            .ifBlank { "暂无正文，点击进入源码编辑…" }

    /**
     * 解析正文中包含的 [[双链节点名]] 引用列表
     */
    val wikiLinks: List<String>
        get() {
            val regex = Regex("\\[\\[([^\\]]+)]]")
            return regex.findAll(markdownContent)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
        }
}
