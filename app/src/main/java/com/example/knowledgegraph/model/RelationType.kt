package com.example.knowledgegraph.model

/**
 * 标准化节点语义关系枚举（2字严谨术语）
 */
enum class RelationType(
    val labelCn: String,
    val symbol: String,
    val description: String,
    val colorHex: String
) {
    RELATED(
        labelCn = "关联",
        symbol = "↔",
        description = "横向主题关联或并列概念",
        colorHex = "#38BDF8"
    ),
    DEPENDS_ON(
        labelCn = "依赖",
        symbol = "⇒",
        description = "前置知识或底层架构依赖",
        colorHex = "#F59E0B"
    ),
    REFERENCES(
        labelCn = "引用",
        symbol = "⇢",
        description = "文献出处、摘录或概念索引",
        colorHex = "#818CF8"
    ),
    EXTENDS(
        labelCn = "扩展",
        symbol = "↗",
        description = "对原概念的深化、演进或派生",
        colorHex = "#10B981"
    ),
    CONTRADICTS(
        labelCn = "冲突",
        symbol = "⚡",
        description = "反例、对立假说或范式排斥",
        colorHex = "#F43F5E"
    );

    companion object {
        fun fromName(name: String): RelationType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: RELATED
        }
    }
}
