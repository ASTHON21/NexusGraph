package com.example.knowledgegraph.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgegraph.data.UserPreferencesRepository
import com.example.knowledgegraph.model.AppUiState
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.repository.KnowledgeRepository
import com.example.knowledgegraph.util.GraphDomainService
import com.example.knowledgegraph.util.GraphLayoutHelper
import com.example.knowledgegraph.util.NodeColorUtil
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
    val selectedRelationFilter: RelationType? = null
)

/**
 * 全局知识图谱工作台 ViewModel：
 * - 将纯领域计算与 JSON 备份编解码委托给 GraphDomainService（O(N+E) 预聚合）
 * - 将瞬态提示消息 (statusMessage) 拆分为独立 StateFlow，避免每次弹出/关闭提示触发全图重算
 * - 支持全量工作区 JSON 导出与恢复导入
 */
class KnowledgeViewModel(
    private val repository: KnowledgeRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val filterState = MutableStateFlow(FilterSelectionState())
    private val statusMessageFlow = MutableStateFlow<String?>(null)

    private val coreGraphState = combine(
        repository.observeAllBoards(),
        repository.observeAllNodes(),
        repository.observeAllEdges(),
        preferencesRepository.preferencesFlow,
        filterState
    ) { boards, nodes, edges, prefs, filter ->
        val boardMap = boards.associateBy { it.id }
        val nodeMap = nodes.associateBy { it.id }

        // 当用户主动选择「全部主题」(isAllBoardsSelected == true) 时，保持 activeBoardId = null；
        // 仅在未选择全部主题且尚无有效 boardId 时，才默认指向第一个主题画板
        val activeBoardId = if (prefs.isAllBoardsSelected) {
            null
        } else {
            prefs.activeBoardId?.takeIf { id -> boardMap.containsKey(id) }
                ?: boards.firstOrNull()?.id
        }

        val activeSelectedId = filter.selectedNodeId
            ?.takeIf { id -> nodeMap.containsKey(id) }
            ?: prefs.lastOpenedNodeId?.takeIf { id -> nodeMap.containsKey(id) }

        val nodeUiList = GraphDomainService.buildNodeUiList(
            nodes = nodes,
            edges = edges,
            boardMap = boardMap,
            activeBoardId = activeBoardId,
            activeSelectedId = activeSelectedId
        )

        val edgeUiList = GraphDomainService.buildEdgeUiList(
            edges = edges,
            nodeMap = nodeMap,
            activeSelectedId = activeSelectedId
        )

        val boardModules = GraphDomainService.buildBoardOverviewModules(
            boards = boards,
            nodes = nodes,
            edges = edges,
            activeBoardId = activeBoardId
        )

        val allTags = GraphDomainService.extractAllTags(nodes)

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
            isLoading = false
        )
    }

    val uiState: StateFlow<AppUiState> = combine(
        coreGraphState,
        statusMessageFlow
    ) { coreState, statusMsg ->
        coreState.copy(statusMessage = statusMsg)
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
            val nextIndex = uiState.value.boards.size + 1
            val cleanName = name.trim().ifEmpty { "主题 $nextIndex" }
            val created = repository.createBoard(
                name = cleanName,
                description = description.trim().ifEmpty { "课题知识空间" },
                colorHex = colorHex
            )
            preferencesRepository.setActiveBoardId(created.id)
            statusMessageFlow.value = "已创建主题「${created.name}」"
            onCreated?.invoke(created)
        }
    }

    fun saveBoard(board: BoardTopic) {
        viewModelScope.launch {
            repository.saveBoard(board)
            statusMessageFlow.value = "已更新主题「${board.name}」"
        }
    }

    fun deleteBoard(boardId: String) {
        viewModelScope.launch {
            val targetName = uiState.value.boards.firstOrNull { it.id == boardId }?.name ?: "主题"
            val remaining = uiState.value.boards.filter { it.id != boardId }
            repository.deleteBoard(boardId)
            preferencesRepository.setActiveBoardId(remaining.firstOrNull()?.id)
            filterState.update { it.copy(selectedNodeId = null) }
            statusMessageFlow.value = "已删除主题「$targetName」及其所属节点"
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
            val currentBoards = uiState.value.boards
            val palette = NodeColorUtil.paletteOptions
            val defaultBoardColor = palette[currentBoards.size % palette.size].hex

            val ensuredBoardId = boardId
                ?.takeIf { id -> currentBoards.any { it.id == id } }
                ?: uiState.value.activeBoardId?.takeIf { id -> currentBoards.any { it.id == id } }
                ?: currentBoards.firstOrNull()?.id
                ?: repository.createBoard(
                    name = "主题 1",
                    description = "基础课题知识空间",
                    colorHex = color.ifBlank { defaultBoardColor }
                ).also { preferencesRepository.setActiveBoardId(it.id) }.id

            val allExistingNodes = uiState.value.nodes
            val boardNodes = allExistingNodes.filter { it.boardId == ensuredBoardId }

            val preferredCenter = when {
                spawnCenterWorld != Offset.Zero -> spawnCenterWorld
                boardNodes.isNotEmpty() -> Offset(
                    x = boardNodes.map { it.x }.average().toFloat(),
                    y = boardNodes.map { it.y }.average().toFloat()
                )
                else -> GraphDomainService.computeBoardAnchorCenter(ensuredBoardId, uiState.value.boards)
            }

            val spawnPos = GraphLayoutHelper.findNonOverlappingSpawnPosition(
                viewportCenterWorld = preferredCenter,
                existingNodes = allExistingNodes
            )

            val nextNodeNumber = allExistingNodes.size + 1
            val cleanTitle = title.trim().ifEmpty { "节点 $nextNodeNumber" }
            val ensuredMarkdown = markdownContent.ifBlank { "# $cleanTitle\n\n" }

            val created = repository.createNode(
                title = cleanTitle,
                markdownContent = ensuredMarkdown,
                tagList = tagList,
                color = color,
                x = spawnPos.x,
                y = spawnPos.y,
                boardId = ensuredBoardId
            )
            preferencesRepository.setLastOpenedNodeId(created.id)
            filterState.update { it.copy(selectedNodeId = created.id) }
            statusMessageFlow.value = "已创建节点「${created.title}」"
            onCreated?.invoke(created)
        }
    }

    fun saveNode(node: NodeData) {
        viewModelScope.launch {
            repository.saveNode(node)
            preferencesRepository.setLastOpenedNodeId(node.id)
            statusMessageFlow.value = "已保存「${node.title}」"
        }
    }

    fun deleteNode(nodeId: String) {
        viewModelScope.launch {
            val targetTitle = uiState.value.nodes.firstOrNull { it.id == nodeId }?.title ?: "节点"
            repository.deleteNode(nodeId)
            filterState.update { state ->
                state.copy(selectedNodeId = if (state.selectedNodeId == nodeId) null else state.selectedNodeId)
            }
            statusMessageFlow.value = "已删除「$targetTitle」"
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
                statusMessageFlow.value = "已保存「${type.labelCn}」连接"
            }
        }
    }

    fun updateEdge(edge: EdgeData) {
        viewModelScope.launch {
            repository.updateEdge(edge)
            statusMessageFlow.value = "已更新「${edge.type.labelCn}」连接"
        }
    }

    fun deleteEdge(edgeId: String) {
        viewModelScope.launch {
            repository.deleteEdge(edgeId)
            statusMessageFlow.value = "已移除关系连线"
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
                        name = "主题 1",
                        description = "基础课题知识空间",
                        colorHex = "#6366F1"
                    ).id

                val baseCenter = if (sourceNode != null) {
                    Offset(sourceNode.x, sourceNode.y)
                } else {
                    GraphDomainService.computeBoardAnchorCenter(targetBoardId, uiState.value.boards)
                }
                val spawnPos = GraphLayoutHelper.findNonOverlappingSpawnPosition(
                    viewportCenterWorld = baseCenter,
                    existingNodes = uiState.value.nodes
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

    fun exportBackupJson(): String {
        val state = uiState.value
        return GraphDomainService.exportWorkspaceToJson(
            boards = state.boards,
            nodes = state.nodes,
            edges = state.edges
        )
    }

    fun importBackupJson(jsonText: String) {
        viewModelScope.launch {
            GraphDomainService.parseWorkspaceFromJson(jsonText)
                .onSuccess { payload ->
                    if (payload.boards.isEmpty() && payload.nodes.isEmpty()) {
                        statusMessageFlow.value = "未解析到有效的主题或节点数据，请检查 JSON 格式"
                    } else {
                        repository.importWorkspaceBackup(payload)
                        statusMessageFlow.value =
                            "成功恢复 ${payload.boards.size} 个主题、${payload.nodes.size} 个节点与 ${payload.edges.size} 条关系"
                    }
                }
                .onFailure {
                    statusMessageFlow.value = "备份恢复失败：请确认粘贴的是完整 JSON 备份内容"
                }
        }
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }
}
