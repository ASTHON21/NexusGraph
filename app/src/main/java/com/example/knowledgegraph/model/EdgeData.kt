package com.example.knowledgegraph.model

/**
 * 节点间有向语义关系（图谱边模型）
 */
data class EdgeData(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val type: RelationType,
    val label: String,
    val createdAt: Long
) {
    val displayLabel: String
        get() = label.ifBlank { type.labelCn }
}
