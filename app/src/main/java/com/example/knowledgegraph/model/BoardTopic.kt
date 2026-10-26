package com.example.knowledgegraph.model

/**
 * 独立课题知识画板空间模型 (Topic Board)
 */
data class BoardTopic(
    val id: String,
    val name: String,
    val description: String,
    val colorHex: String,
    val createdAt: Long,
    val sortOrder: Int = 0
) {
    companion object {
        const val BOARD_METHODOLOGY = "board_methodology"
        const val BOARD_ARCHITECTURE = "board_architecture"
        const val BOARD_WORKFLOW = "board_workflow"
    }
}
