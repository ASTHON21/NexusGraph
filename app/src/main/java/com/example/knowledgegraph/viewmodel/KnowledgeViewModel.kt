package com.example.knowledgegraph.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgegraph.data.UserPreferencesRepository
import com.example.knowledgegraph.model.AppUiState
import com.example.knowledgegraph.model.BoardOverviewModule
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.EdgeUiState
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.NodeUiState
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.repository.KnowledgeRepository
import com.example.knowledgegraph.util.GraphLayoutHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class FilterSelectionState(
    val selectedNodeId: String? = null,
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val selectedRelationFilter: RelationType? = null,
    val statusMessage: String? = null
)

/**
 * 全局知识图谱工作台 ViewModel
 * 负责聚合各主题画板模块概览指标、跨主题节点投影、无碰撞坐标分配及关系去重更新
 */
class KnowledgeViewModel(
    private val repository: KnowledgeRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val filterState = MutableStateFlow(FilterSelectionState())

    val uiState: StateFlow<AppUiState> = combine(
        repository.observeAllBoards(),
        repository.observeAllNodes(),
        repository.observeAllEdges(),
        preferencesRepository.preferencesFlow,
        filterState
    ) { boards, nodes, edges, prefs, filter ->
        val boardMap = boards.associateBy { it.id }
        val nodeMap = nodes.associateBy { it.id }

        val activeBoardId = prefs.activeBoardId?.takeIf { id -> boardMap.containsKey(id) }
            ?: boards.firstOrNull()?.id
        val activeSelectedId = filter.selectedNodeId
            ?.takeIf { id -> nodeMap.containsKey(id) }
            ?: prefs.lastOpenedNodeId?.takeIf { id -> nodeMap.containsKey(id) }

        val highlightedNeighborIds = if (activeSelectedId != null) {
            edges.mapNotNull { edge ->
                when (edge.fromNodeId) {
                    activeSelectedId -> edge.toNodeId
                    else -> if (edge.toNodeId == activeSelectedId) edge.fromNodeId else null
                }
            }.toSet()
        } else {
            emptySet()
        }

        val nodeUiList = nodes.map { node ->
            val incoming = edges.count { it.toNodeId == node.id }
            val outgoing = edges.count { it.fromNodeId == node.id }
            val board = boardMap[node.boardId]
            val isCross = activeBoardId != null && node.boardId != activeBoardId
            NodeUiState(
                node = node,
                boardName = board?.name ?: "未分类",
                boardColorHex = board?.colorHex ?: node.color,
                isCrossBoard = isCross,
                isSelected = node.id == activeSelectedId,
                isHighlighted = node.id == activeSelectedId || node.id in highlightedNeighborIds,
                incomingCount = incoming,
                outgoingCount = outgoing
            )
        }

        val edgeUiList = edges.map { edge ->
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

        // 计算每个主题画板的独立模块概览数据（主题、节点、内部关系、跨界关系、标签、最近更新）
        val boardModules = boards.map { board ->
            val boardNodes = nodes.filter { it.boardId == board.id }
            val boardNodeIds = boardNodes.map { it.id }.toSet()

            var internalCount = 0
            var crossCount = 0
            val relCounts = mutableMapOf<RelationType, Int>()

            edges.forEach { edge ->
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
            boardNodes.forEach { n ->
                n.tagList.forEach { t ->
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

        val allTags = nodes
            .flatMap { it.tagList }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        AppUiState(
            boards = boards,
            boardModules = boardModules,
            activeBoardId = activeBoardId,
            showCrossBoardNodes = prefs.showCrossBoardNodes,
            nodes = nodes,
            edges = edges,
            nodeUiList = nodeUiList,
            edgeUiList = edgeUiList,
            allTags = allTags,
            selectedNodeId = activeSelectedId,
            lastOpenedNodeId = prefs.lastOpenedNodeId,
            searchQuery = filter.searchQuery,
            selectedTag = filter.selectedTag,
            selectedRelationFilter = filter.selectedRelationFilter,
            isDarkTheme = prefs.isDarkTheme,
            showEdgeLabels = prefs.showEdgeLabels,
            showCanvasGrid = prefs.showCanvasGrid,
            isLoading = false,
            statusMessage = filter.statusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppUiState(isLoading = true)
    )

    fun selectActiveBoard(boardId: String?) {
        viewModelScope.launch {
            preferencesRepository.setActiveBoardId(boardId)
        }
    }

    fun toggleShowCrossBoardNodes() {
        viewModelScope.launch {
            preferencesRepository.setShowCrossBoardNodes(!uiState.value.showCrossBoardNodes)
        }
    }

    fun createBoard(
        name: String,
        description: String,
        colorHex: String,
        onCreated: ((BoardTopic) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val created = repository.createBoard(
                name = name,
                description = description,
                colorHex = colorHex
            )
            preferencesRepository.setActiveBoardId(created.id)
            filterState.update {
                it.copy(statusMessage = "已创建主题「${created.name}」")
            }
            onCreated?.invoke(created)
        }
    }

    fun saveBoard(board: BoardTopic) {
        viewModelScope.launch {
            repository.saveBoard(board)
            filterState.update {
                it.copy(statusMessage = "已更新主题「${board.name}」")
            }
        }
    }

    fun deleteBoard(boardId: String) {
        viewModelScope.launch {
            val targetName = uiState.value.boards.firstOrNull { it.id == boardId }?.name ?: "主题"
            repository.deleteBoard(boardId)
            val remaining = uiState.value.boards.filter { it.id != boardId }
            preferencesRepository.setActiveBoardId(remaining.firstOrNull()?.id)
            filterState.update {
                it.copy(
                    selectedNodeId = null,
                    statusMessage = "已删除主题「$targetName」及其所属节点"
                )
            }
        }
    }

    fun selectNode(nodeId: String?) {
        filterState.update { it.copy(selectedNodeId = nodeId) }
        if (nodeId != null) {
            viewModelScope.launch {
                preferencesRepository.setLastOpenedNodeId(nodeId)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        filterState.update { it.copy(searchQuery = query) }
    }

    fun selectTagFilter(tag: String?) {
        filterState.update { state ->
            val nextTag = if (state.selectedTag == tag) null else tag
            state.copy(selectedTag = nextTag)
        }
    }

    fun selectRelationFilter(type: RelationType?) {
        filterState.update { state ->
            val nextType = if (state.selectedRelationFilter == type) null else type
            state.copy(selectedRelationFilter = nextType)
        }
    }

    fun clearFilters() {
        filterState.update {
            it.copy(
                searchQuery = "",
                selectedTag = null,
                selectedRelationFilter = null
            )
        }
    }

    fun createNewNode(
        title: String,
        markdownContent: String,
        tagList: List<String>,
        color: String,
        boardId: String? = null,
        spawnCenterWorld: Offset = Offset.Zero,
        onCreated: ((NodeData) -> Unit)? = null
    ) {
        viewModelScope.launch {
            // 若当前尚未创建任何主题画板，先自动创建一个默认主题画板以保证外键完整性
            val ensuredBoardId = boardId
                ?.takeIf { id -> uiState.value.boards.any { it.id == id } }
                ?: uiState.value.activeBoardId?.takeIf { id -> uiState.value.boards.any { it.id == id } }
                ?: uiState.value.boards.firstOrNull()?.id
                ?: repository.createBoard(
                    name = "默认主题",
                    description = "基础课题知识空间",
                    colorHex = color
                ).also { preferencesRepository.setActiveBoardId(it.id) }.id

            val boardNodes = uiState.value.nodes.filter { it.boardId == ensuredBoardId }
            val spawnPos = GraphLayoutHelper.findNonOverlappingSpawnPosition(
                viewportCenterWorld = spawnCenterWorld,
                existingNodes = boardNodes
            )

            val created = repository.createNode(
                title = title,
                markdownContent = markdownContent,
                tagList = tagList,
                color = color,
                x = spawnPos.x,
                y = spawnPos.y,
                boardId = ensuredBoardId
            )
            preferencesRepository.setLastOpenedNodeId(created.id)
            filterState.update {
                it.copy(
                    selectedNodeId = created.id,
                    statusMessage = "已创建节点「${created.title}」"
                )
            }
            onCreated?.invoke(created)
        }
    }

    fun saveNode(node: NodeData) {
        viewModelScope.launch {
            repository.saveNode(node)
            preferencesRepository.setLastOpenedNodeId(node.id)
            filterState.update {
                it.copy(statusMessage = "已保存「${node.title}」")
            }
        }
    }

    fun deleteNode(nodeId: String) {
        viewModelScope.launch {
            val targetTitle = uiState.value.nodes.firstOrNull { it.id == nodeId }?.title ?: "节点"
            repository.deleteNode(nodeId)
            filterState.update { state ->
                state.copy(
                    selectedNodeId = if (state.selectedNodeId == nodeId) null else state.selectedNodeId,
                    statusMessage = "已删除「$targetTitle」"
                )
            }
        }
    }

    fun createEdge(
        fromNodeId: String,
        toNodeId: String,
        type: RelationType,
        label: String
    ) {
        viewModelScope.launch {
            val created: EdgeData? = repository.createOrUpdateEdge(
                fromNodeId = fromNodeId,
                toNodeId = toNodeId,
                type = type,
                label = label
            )
            if (created != null) {
                filterState.update {
                    it.copy(statusMessage = "已保存「${type.labelCn}」连接")
                }
            }
        }
    }

    fun updateEdge(edge: EdgeData) {
        viewModelScope.launch {
            repository.updateEdge(edge)
            filterState.update {
                it.copy(statusMessage = "已更新「${edge.type.labelCn}」连接")
            }
        }
    }

    fun deleteEdge(edgeId: String) {
        viewModelScope.launch {
            repository.deleteEdge(edgeId)
            filterState.update {
                it.copy(statusMessage = "已移除关系连线")
            }
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            preferencesRepository.setDarkTheme(!uiState.value.isDarkTheme)
        }
    }

    fun toggleShowEdgeLabels() {
        viewModelScope.launch {
            preferencesRepository.setShowEdgeLabels(!uiState.value.showEdgeLabels)
        }
    }

    fun toggleShowCanvasGrid() {
        viewModelScope.launch {
            preferencesRepository.setShowCanvasGrid(!uiState.value.showCanvasGrid)
        }
    }

    fun openOrCreateNodeByWikiTitle(
        sourceNodeId: String?,
        wikiTitle: String,
        onNavigateToNode: (String) -> Unit
    ) {
        val cleaned = wikiTitle.trim()
        if (cleaned.isEmpty()) return
        val existing = uiState.value.nodes.firstOrNull {
            it.title.equals(cleaned, ignoreCase = true)
        }
        if (existing != null) {
            selectNode(existing.id)
            onNavigateToNode(existing.id)
        } else {
            viewModelScope.launch {
                val sourceNode = sourceNodeId?.let { id ->
                    uiState.value.nodes.firstOrNull { it.id == id }
                }
                val targetBoardId = sourceNode?.boardId
                    ?: uiState.value.activeBoardId
                    ?: uiState.value.boards.firstOrNull()?.id
                    ?: repository.createBoard(
                        name = "默认主题",
                        description = "基础课题知识空间",
                        colorHex = "#6366F1"
                    ).id

                val baseCenter = if (sourceNode != null) {
                    Offset(sourceNode.x, sourceNode.y)
                } else {
                    Offset.Zero
                }
                val spawnPos = GraphLayoutHelper.findNonOverlappingSpawnPosition(
                    viewportCenterWorld = baseCenter,
                    existingNodes = uiState.value.nodes.filter { it.boardId == targetBoardId }
                )

                val created = repository.createNode(
                    title = cleaned,
                    markdownContent = "# $cleaned\n\n",
                    tagList = emptyList(),
                    color = sourceNode?.color ?: "#0EA5E9",
                    x = spawnPos.x,
                    y = spawnPos.y,
                    boardId = targetBoardId
                )
                if (sourceNodeId != null && sourceNodeId != created.id) {
                    repository.createOrUpdateEdge(
                        fromNodeId = sourceNodeId,
                        toNodeId = created.id,
                        type = RelationType.REFERENCES,
                        label = "双链引用"
                    )
                }
                selectNode(created.id)
                onNavigateToNode(created.id)
            }
        }
    }

    fun clearStatusMessage() {
        filterState.update { it.copy(statusMessage = null) }
    }
}
