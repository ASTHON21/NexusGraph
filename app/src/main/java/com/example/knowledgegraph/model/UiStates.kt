package com.example.knowledgegraph.model

/**
 * 单个节点的 UI 包装状态（包含连接统计、所属主题画板信息与跨主题标识）
 */
data class NodeUiState(
    val node: NodeData,
    val boardName: String = "未分类",
    val boardColorHex: String = "#6366F1",
    val isCrossBoard: Boolean = false,
    val isSelected: Boolean = false,
    val isHighlighted: Boolean = false,
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0
) {
    val totalDegree: Int get() = incomingCount + outgoingCount
}

/**
 * 单条关系边的 UI 包装状态（包含端点节点快照与跨主题标识）
 */
data class EdgeUiState(
    val edge: EdgeData,
    val fromNode: NodeData?,
    val toNode: NodeData?,
    val isCrossBoard: Boolean = false,
    val isHighlighted: Boolean = false
)

/**
 * 总览状态下每个主题画板模块的聚合概览模型
 */
data class BoardOverviewModule(
    val board: BoardTopic,
    val isSelected: Boolean,
    val nodeCount: Int,
    val internalEdgeCount: Int,
    val crossBoardEdgeCount: Int,
    val tagCount: Int,
    val topTags: List<String>,
    val relationCounts: Map<RelationType, Int>,
    val latestNodeTitle: String?,
    val latestUpdatedAt: Long
) {
    val totalEdgeCount: Int get() = internalEdgeCount + crossBoardEdgeCount
}

/**
 * Markdown 编辑器视图切换模式（2字严谨术语）
 */
enum class EditorViewMode(val label: String) {
    EDIT("源码"),
    SPLIT("分屏"),
    PREVIEW("预览")
}

/**
 * 画板排布模式（2字标准术语）
 */
enum class GraphLayoutMode(val label: String) {
    MANUAL("自由"),
    FORCE("力导"),
    RADIAL("辐射"),
    CIRCULAR("环形"),
    GRID("网格")
}

/**
 * 全局工作台 UI 状态
 */
data class AppUiState(
    val boards: List<BoardTopic> = emptyList(),
    val boardModules: List<BoardOverviewModule> = emptyList(),
    val activeBoardId: String? = null,
    val showCrossBoardNodes: Boolean = true,
    val nodes: List<NodeData> = emptyList(),
    val edges: List<EdgeData> = emptyList(),
    val nodeUiList: List<NodeUiState> = emptyList(),
    val edgeUiList: List<EdgeUiState> = emptyList(),
    val allTags: List<String> = emptyList(),
    val selectedNodeId: String? = null,
    val lastOpenedNodeId: String? = null,
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val selectedRelationFilter: RelationType? = null,
    val isDarkTheme: Boolean = true,
    val showEdgeLabels: Boolean = true,
    val showCanvasGrid: Boolean = true,
    val isLoading: Boolean = true,
    val statusMessage: String? = null
) {
    val boardMap: Map<String, BoardTopic>
        get() = boards.associateBy { it.id }

    val activeBoard: BoardTopic?
        get() = activeBoardId?.let { id -> boards.firstOrNull { it.id == id } }

    val selectedNode: NodeData?
        get() = nodes.firstOrNull { it.id == selectedNodeId }

    /**
     * 当前画板可见节点集合：
     * - 当 activeBoardId == null（全部主题）时，包含所有节点；
     * - 当指定特定主题画板时，包含该画板原生节点 + （开启“跨界”时）与原生节点存在直接连线的跨主题外部节点
     */
    val canvasNodeUiList: List<NodeUiState>
        get() {
            val baseList = if (activeBoardId == null) {
                nodeUiList
            } else {
                val nativeIds = nodes.filter { it.boardId == activeBoardId }.map { it.id }.toSet()
                val crossLinkedIds = if (showCrossBoardNodes) {
                    edges.mapNotNull { edge ->
                        when {
                            edge.fromNodeId in nativeIds && edge.toNodeId !in nativeIds -> edge.toNodeId
                            edge.toNodeId in nativeIds && edge.fromNodeId !in nativeIds -> edge.fromNodeId
                            else -> null
                        }
                    }.toSet()
                } else {
                    emptySet()
                }
                nodeUiList.filter { it.node.id in nativeIds || it.node.id in crossLinkedIds }
            }
            return baseList.filter { matchesSearchAndFilter(it.node) }
        }

    val filteredNodes: List<NodeData>
        get() = canvasNodeUiList.map { it.node }

    /**
     * 当前画板可见语义边集合（两端节点均在当前画板可见集合内）
     */
    val canvasEdges: List<EdgeData>
        get() {
            val visibleIds = canvasNodeUiList.map { it.node.id }.toSet()
            return edges.filter { it.fromNodeId in visibleIds && it.toNodeId in visibleIds }
        }

    private fun matchesSearchAndFilter(node: NodeData): Boolean {
        val matchesTag = selectedTag == null || node.tagList.contains(selectedTag)
        val q = searchQuery.trim()
        val matchesQuery = q.isEmpty() ||
            node.title.contains(q, ignoreCase = true) ||
            node.markdownContent.contains(q, ignoreCase = true) ||
            node.tagList.any { it.contains(q, ignoreCase = true) }
        val matchesRelation = if (selectedRelationFilter == null) {
            true
        } else {
            edges.any {
                it.type == selectedRelationFilter &&
                    (it.fromNodeId == node.id || it.toNodeId == node.id)
            }
        }
        return matchesTag && matchesQuery && matchesRelation
    }
}
