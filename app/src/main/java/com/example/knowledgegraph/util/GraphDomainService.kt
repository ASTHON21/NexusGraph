package com.example.knowledgegraph.util

import androidx.compose.ui.geometry.Offset
import com.example.knowledgegraph.model.BoardOverviewModule
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.EdgeUiState
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.NodeUiState
import com.example.knowledgegraph.model.RelationType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 知识库全量备份载荷（包含主题画板、节点与有向语义边）
 */
data class WorkspaceBackupPayload(
    val boards: List<BoardTopic>,
    val nodes: List<NodeData>,
    val edges: List<EdgeData>
)

/**
 * 知识图谱纯领域业务计算与备份编解码服务：
 * 将图结构聚合计算、多主题扇区坐标分配、双链提取与全量 JSON 备份逻辑从 ViewModel 中解耦，
 * 便于独立单元测试与性能优化。
 */
object GraphDomainService {

    /**
     * 为不同主题画板分配专属环形扇区基准坐标，避免不同主题的首个节点全部叠在 (0, 0)
     */
    fun computeBoardAnchorCenter(boardId: String, boards: List<BoardTopic>): Offset {
        val boardIndex = boards.indexOfFirst { it.id == boardId }.coerceAtLeast(0)
        if (boardIndex == 0) return Offset.Zero
        val sectorAngle = boardIndex * (2.0 * PI / 6.0)
        val ringRadius = 420f * ((boardIndex - 1) / 6 + 1)
        return Offset(
            x = (ringRadius * cos(sectorAngle)).toFloat(),
            y = (ringRadius * sin(sectorAngle)).toFloat()
        )
    }

    /**
     * 计算与当前选中节点直接相连的一跳邻居 ID 集合
     */
    fun computeHighlightedNeighborIds(
        activeSelectedId: String?,
        edges: List<EdgeData>
    ): Set<String> {
        if (activeSelectedId == null) return emptySet()
        val result = HashSet<String>()
        for (edge in edges) {
            if (edge.fromNodeId == activeSelectedId) {
                result.add(edge.toNodeId)
            } else if (edge.toNodeId == activeSelectedId) {
                result.add(edge.fromNodeId)
            }
        }
        return result
    }

    /**
     * 将底层 NodeData 列表与边统计转换为 UI 渲染所需的 NodeUiState 列表（预聚合出入度，O(N + E) 复杂度）
     */
    fun buildNodeUiList(
        nodes: List<NodeData>,
        edges: List<EdgeData>,
        boardMap: Map<String, BoardTopic>,
        activeBoardId: String?,
        activeSelectedId: String?
    ): List<NodeUiState> {
        val incomingCounts = HashMap<String, Int>(nodes.size)
        val outgoingCounts = HashMap<String, Int>(nodes.size)
        for (edge in edges) {
            outgoingCounts[edge.fromNodeId] = (outgoingCounts[edge.fromNodeId] ?: 0) + 1
            incomingCounts[edge.toNodeId] = (incomingCounts[edge.toNodeId] ?: 0) + 1
        }
        val highlightedNeighbors = computeHighlightedNeighborIds(activeSelectedId, edges)

        return nodes.map { node ->
            val board = boardMap[node.boardId]
            val isCross = activeBoardId != null && node.boardId != activeBoardId
            NodeUiState(
                node = node,
                boardName = board?.name ?: "未分类",
                boardColorHex = board?.colorHex ?: node.color,
                isCrossBoard = isCross,
                isSelected = node.id == activeSelectedId,
                isHighlighted = node.id == activeSelectedId || node.id in highlightedNeighbors,
                incomingCount = incomingCounts[node.id] ?: 0,
                outgoingCount = outgoingCounts[node.id] ?: 0
            )
        }
    }

    /**
     * 将底层 EdgeData 列表转换为 EdgeUiState 列表
     */
    fun buildEdgeUiList(
        edges: List<EdgeData>,
        nodeMap: Map<String, NodeData>,
        activeSelectedId: String?
    ): List<EdgeUiState> {
        return edges.map { edge ->
            val fromNode = nodeMap[edge.fromNodeId]
            val toNode = nodeMap[edge.toNodeId]
            val isCrossEdge = fromNode != null && toNode != null && fromNode.boardId != toNode.boardId
            EdgeUiState(
                edge = edge,
                fromNode = fromNode,
                toNode = toNode,
                isCrossBoard = isCrossEdge,
                isHighlighted = activeSelectedId != null &&
                    (edge.fromNodeId == activeSelectedId || edge.toNodeId == activeSelectedId)
            )
        }
    }

    /**
     * 聚合各主题画板在总览页的统计卡片数据
     */
    fun buildBoardOverviewModules(
        boards: List<BoardTopic>,
        nodes: List<NodeData>,
        edges: List<EdgeData>,
        activeBoardId: String?
    ): List<BoardOverviewModule> {
        val nodesByBoard = nodes.groupBy { it.boardId }
        return boards.map { board ->
            val boardNodes = nodesByBoard[board.id].orEmpty()
            val boardNodeIds = boardNodes.mapTo(HashSet()) { it.id }

            var internalCount = 0
            var crossCount = 0
            val relCounts = mutableMapOf<RelationType, Int>()

            for (edge in edges) {
                val fromIn = edge.fromNodeId in boardNodeIds
                val toIn = edge.toNodeId in boardNodeIds
                if (fromIn && toIn) {
                    internalCount++
                    relCounts[edge.type] = (relCounts[edge.type] ?: 0) + 1
                } else if (fromIn || toIn) {
                    crossCount++
                    relCounts[edge.type] = (relCounts[edge.type] ?: 0) + 1
                }
            }

            val tagFreq = mutableMapOf<String, Int>()
            for (n in boardNodes) {
                for (t in n.tagList) {
                    val clean = t.trim()
                    if (clean.isNotEmpty()) {
                        tagFreq[clean] = (tagFreq[clean] ?: 0) + 1
                    }
                }
            }
            val sortedTags = tagFreq.entries
                .sortedByDescending { it.value }
                .map { it.key }

            val latestNode = boardNodes.maxByOrNull { it.updatedAt }

            BoardOverviewModule(
                board = board,
                isSelected = board.id == activeBoardId,
                nodeCount = boardNodes.size,
                internalEdgeCount = internalCount,
                crossBoardEdgeCount = crossCount,
                tagCount = sortedTags.size,
                topTags = sortedTags.take(5),
                relationCounts = relCounts,
                latestNodeTitle = latestNode?.title,
                latestUpdatedAt = latestNode?.updatedAt ?: board.createdAt
            )
        }
    }

    fun extractAllTags(nodes: List<NodeData>): List<String> {
        return nodes
            .flatMap { it.tagList }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    /**
     * 将全量知识图谱序列化为结构化 JSON 备份字符串（纯 Kotlin 实现，兼容 JVM 单元测试与 Android 运行时）
     */
    fun exportWorkspaceToJson(
        boards: List<BoardTopic>,
        nodes: List<NodeData>,
        edges: List<EdgeData>
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": 1,\n")
        sb.append("  \"exportedAt\": ").append(System.currentTimeMillis()).append(",\n")

        // boards
        sb.append("  \"boards\": [\n")
        boards.forEachIndexed { idx, b ->
            sb.append("    {")
            sb.append("\"id\":\"").append(escapeJson(b.id)).append("\",")
            sb.append("\"name\":\"").append(escapeJson(b.name)).append("\",")
            sb.append("\"description\":\"").append(escapeJson(b.description)).append("\",")
            sb.append("\"colorHex\":\"").append(escapeJson(b.colorHex)).append("\",")
            sb.append("\"createdAt\":").append(b.createdAt).append(",")
            sb.append("\"sortOrder\":").append(b.sortOrder)
            sb.append("}")
            if (idx < boards.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // nodes
        sb.append("  \"nodes\": [\n")
        nodes.forEachIndexed { idx, n ->
            sb.append("    {")
            sb.append("\"id\":\"").append(escapeJson(n.id)).append("\",")
            sb.append("\"title\":\"").append(escapeJson(n.title)).append("\",")
            sb.append("\"markdownContent\":\"").append(escapeJson(n.markdownContent)).append("\",")
            sb.append("\"tags\":\"").append(escapeJson(n.tagList.joinToString("|"))).append("\",")
            sb.append("\"color\":\"").append(escapeJson(n.color)).append("\",")
            sb.append("\"x\":").append(n.x).append(",")
            sb.append("\"y\":").append(n.y).append(",")
            sb.append("\"createdAt\":").append(n.createdAt).append(",")
            sb.append("\"updatedAt\":").append(n.updatedAt).append(",")
            sb.append("\"boardId\":\"").append(escapeJson(n.boardId)).append("\"")
            sb.append("}")
            if (idx < nodes.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // edges
        sb.append("  \"edges\": [\n")
        edges.forEachIndexed { idx, e ->
            sb.append("    {")
            sb.append("\"id\":\"").append(escapeJson(e.id)).append("\",")
            sb.append("\"fromNodeId\":\"").append(escapeJson(e.fromNodeId)).append("\",")
            sb.append("\"toNodeId\":\"").append(escapeJson(e.toNodeId)).append("\",")
            sb.append("\"type\":\"").append(escapeJson(e.type.name)).append("\",")
            sb.append("\"label\":\"").append(escapeJson(e.label)).append("\",")
            sb.append("\"createdAt\":").append(e.createdAt)
            sb.append("}")
            if (idx < edges.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    /**
     * 从 JSON 备份字符串解析还原主题画板、节点与边集合
     */
    fun parseWorkspaceFromJson(jsonText: String): Result<WorkspaceBackupPayload> = runCatching {
        val trimmed = jsonText.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) { "无效的 JSON 备份格式" }

        val boardsSection = extractJsonArraySection(trimmed, "boards")
        val nodesSection = extractJsonArraySection(trimmed, "nodes")
        val edgesSection = extractJsonArraySection(trimmed, "edges")

        val boards = parseObjectMaps(boardsSection).mapIndexed { index, map ->
            BoardTopic(
                id = map["id"].orEmpty().ifBlank { "board_imp_$index" },
                name = map["name"].orEmpty().ifBlank { "主题 ${index + 1}" },
                description = map["description"].orEmpty().ifBlank { "课题知识空间" },
                colorHex = map["colorHex"].orEmpty().ifBlank { "#6366F1" },
                createdAt = map["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
                sortOrder = map["sortOrder"]?.toIntOrNull() ?: index
            )
        }

        val fallbackBoardId = boards.firstOrNull()?.id ?: "board_default"
        val nodes = parseObjectMaps(nodesSection).mapIndexed { index, map ->
            val rawTags = map["tags"].orEmpty()
            val tags = rawTags.split("|", ",").map { it.trim() }.filter { it.isNotEmpty() }
            NodeData(
                id = map["id"].orEmpty().ifBlank { "node_imp_$index" },
                title = map["title"].orEmpty().ifBlank { "节点 ${index + 1}" },
                markdownContent = map["markdownContent"].orEmpty(),
                tagList = tags,
                color = map["color"].orEmpty().ifBlank { "#6366F1" },
                x = map["x"]?.toFloatOrNull() ?: 0f,
                y = map["y"]?.toFloatOrNull() ?: 0f,
                createdAt = map["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
                updatedAt = map["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
                boardId = map["boardId"].orEmpty().ifBlank { fallbackBoardId }
            )
        }

        val edges = parseObjectMaps(edgesSection).mapIndexed { index, map ->
            val typeName = map["type"].orEmpty()
            val relType = RelationType.entries.firstOrNull {
                it.name.equals(typeName, ignoreCase = true)
            } ?: RelationType.RELATED
            EdgeData(
                id = map["id"].orEmpty().ifBlank { "edge_imp_$index" },
                fromNodeId = map["fromNodeId"].orEmpty(),
                toNodeId = map["toNodeId"].orEmpty(),
                type = relType,
                label = map["label"].orEmpty().ifBlank { relType.labelCn },
                createdAt = map["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
            )
        }.filter { it.fromNodeId.isNotBlank() && it.toNodeId.isNotBlank() && it.fromNodeId != it.toNodeId }

        WorkspaceBackupPayload(
            boards = boards,
            nodes = nodes,
            edges = edges
        )
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unescapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun extractJsonArraySection(json: String, arrayKey: String): String {
        val keyToken = "\"$arrayKey\""
        val keyIdx = json.indexOf(keyToken)
        if (keyIdx < 0) return ""
        val openBracket = json.indexOf('[', startIndex = keyIdx + keyToken.length)
        if (openBracket < 0) return ""
        var depth = 0
        var inQuotes = false
        var escaped = false
        for (i in openBracket until json.length) {
            val c = json[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inQuotes = !inQuotes
                continue
            }
            if (!inQuotes) {
                if (c == '[') depth++
                else if (c == ']') {
                    depth--
                    if (depth == 0) {
                        return json.substring(openBracket + 1, i)
                    }
                }
            }
        }
        return ""
    }

    private fun parseObjectMaps(arrayContent: String): List<Map<String, String>> {
        if (arrayContent.isBlank()) return emptyList()
        val objects = mutableListOf<String>()
        var depth = 0
        var objStart = -1
        var inQuotes = false
        var escaped = false

        for (i in arrayContent.indices) {
            val c = arrayContent[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inQuotes = !inQuotes
                continue
            }
            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) objStart = i + 1
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        objects.add(arrayContent.substring(objStart, i))
                        objStart = -1
                    }
                }
            }
        }

        val kvRegex = Regex("\"([^\"]+)\"\\s*:\\s*(\"((?:\\\\.|[^\"\\\\])*)\"|([^,}\\s]+))")
        return objects.map { objStr ->
            val map = LinkedHashMap<String, String>()
            for (match in kvRegex.findAll(objStr)) {
                val key = match.groupValues[1]
                val rawValue = if (match.groups[3] != null) {
                    unescapeJson(match.groupValues[3])
                } else {
                    match.groupValues[4]
                }
                map[key] = rawValue
            }
            map
        }
    }
}
