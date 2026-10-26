package com.example.knowledgegraph.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgegraph.data.UserPreferencesRepository
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.EdgeUiState
import com.example.knowledgegraph.model.EditorViewMode
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.repository.KnowledgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NodeDetailScreenUiState(
    val nodeId: String = "",
    val originalNode: NodeData? = null,
    val boards: List<BoardTopic> = emptyList(),
    val boardIdDraft: String = "",
    val titleDraft: String = "",
    val markdownDraft: String = "",
    val tagListDraft: List<String> = emptyList(),
    val colorDraft: String = "#6366F1",
    val editorViewMode: EditorViewMode = EditorViewMode.SPLIT,
    val outgoingEdges: List<EdgeUiState> = emptyList(),
    val incomingEdges: List<EdgeUiState> = emptyList(),
    val backlinkNodes: List<NodeData> = emptyList(),
    val availableTargetNodes: List<NodeData> = emptyList(),
    val hasUnsavedChanges: Boolean = false,
    val saveBannerMessage: String? = null
)

/**
 * 节点详情与 Markdown 编辑器 ViewModel
 */
class NodeDetailViewModel(
    private val repository: KnowledgeRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NodeDetailScreenUiState())
    val uiState: StateFlow<NodeDetailScreenUiState> = _uiState.asStateFlow()

    private var boundNodeId: String? = null

    fun bindNode(nodeId: String) {
        if (boundNodeId == nodeId) return
        boundNodeId = nodeId

        viewModelScope.launch {
            preferencesRepository.setLastOpenedNodeId(nodeId)
        }

        viewModelScope.launch {
            combine(
                repository.observeAllBoards(),
                repository.observeNodeById(nodeId),
                repository.observeAllNodes(),
                repository.observeAllEdges()
            ) { boards, currentNode, allNodes, allEdges ->
                listOf(boards, currentNode, allNodes, allEdges)
            }.collect { tuple ->
                @Suppress("UNCHECKED_CAST")
                val boards = tuple[0] as List<BoardTopic>
                val currentNode = tuple[1] as? NodeData ?: return@collect
                @Suppress("UNCHECKED_CAST")
                val allNodes = tuple[2] as List<NodeData>
                @Suppress("UNCHECKED_CAST")
                val allEdges = tuple[3] as List<EdgeData>

                val nodeMap = allNodes.associateBy { it.id }

                val outgoing = allEdges
                    .filter { it.fromNodeId == nodeId }
                    .map { edge ->
                        val target = nodeMap[edge.toNodeId]
                        EdgeUiState(
                            edge = edge,
                            fromNode = currentNode,
                            toNode = target,
                            isCrossBoard = target != null && target.boardId != currentNode.boardId,
                            isHighlighted = true
                        )
                    }

                val incoming = allEdges
                    .filter { it.toNodeId == nodeId }
                    .map { edge ->
                        val source = nodeMap[edge.fromNodeId]
                        EdgeUiState(
                            edge = edge,
                            fromNode = source,
                            toNode = currentNode,
                            isCrossBoard = source != null && source.boardId != currentNode.boardId,
                            isHighlighted = true
                        )
                    }

                val backlinks = allNodes.filter { other ->
                    other.id != currentNode.id &&
                        other.wikiLinks.any { it.equals(currentNode.title, ignoreCase = true) }
                }

                val candidates = allNodes.filter { it.id != nodeId }

                _uiState.update { prev ->
                    val shouldInitializeDrafts = prev.originalNode == null ||
                        prev.nodeId != nodeId ||
                        !prev.hasUnsavedChanges

                    if (shouldInitializeDrafts) {
                        prev.copy(
                            nodeId = nodeId,
                            originalNode = currentNode,
                            boards = boards,
                            boardIdDraft = currentNode.boardId,
                            titleDraft = currentNode.title,
                            markdownDraft = currentNode.markdownContent,
                            tagListDraft = currentNode.tagList,
                            colorDraft = currentNode.color,
                            outgoingEdges = outgoing,
                            incomingEdges = incoming,
                            backlinkNodes = backlinks,
                            availableTargetNodes = candidates,
                            hasUnsavedChanges = false
                        )
                    } else {
                        prev.copy(
                            originalNode = currentNode,
                            boards = boards,
                            outgoingEdges = outgoing,
                            incomingEdges = incoming,
                            backlinkNodes = backlinks,
                            availableTargetNodes = candidates
                        )
                    }
                }
            }
        }
    }

    fun updateBoardIdDraft(newBoardId: String) {
        _uiState.update { state ->
            state.copy(
                boardIdDraft = newBoardId,
                hasUnsavedChanges = true,
                saveBannerMessage = null
            )
        }
    }

    fun updateTitleDraft(newTitle: String) {
        _uiState.update { state ->
            state.copy(
                titleDraft = newTitle,
                hasUnsavedChanges = true,
                saveBannerMessage = null
            )
        }
    }

    fun updateMarkdownDraft(newMarkdown: String) {
        _uiState.update { state ->
            state.copy(
                markdownDraft = newMarkdown,
                hasUnsavedChanges = true,
                saveBannerMessage = null
            )
        }
    }

    fun updateColorDraft(newColorHex: String) {
        _uiState.update { state ->
            state.copy(
                colorDraft = newColorHex,
                hasUnsavedChanges = true,
                saveBannerMessage = null
            )
        }
    }

    fun addTag(tag: String) {
        val cleaned = tag.trim().removePrefix("#").trim()
        if (cleaned.isEmpty()) return
        _uiState.update { state ->
            if (state.tagListDraft.any { it.equals(cleaned, ignoreCase = true) }) {
                state
            } else {
                state.copy(
                    tagListDraft = state.tagListDraft + cleaned,
                    hasUnsavedChanges = true,
                    saveBannerMessage = null
                )
            }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { state ->
            state.copy(
                tagListDraft = state.tagListDraft.filterNot { it == tag },
                hasUnsavedChanges = true,
                saveBannerMessage = null
            )
        }
    }

    fun setEditorViewMode(mode: EditorViewMode) {
        _uiState.update { it.copy(editorViewMode = mode) }
    }

    fun saveChanges() {
        val state = _uiState.value
        val base = state.originalNode ?: return
        viewModelScope.launch {
            val updatedNode = base.copy(
                title = state.titleDraft.trim().ifEmpty { "未命名节点" },
                markdownContent = state.markdownDraft,
                tagList = state.tagListDraft,
                color = state.colorDraft,
                boardId = state.boardIdDraft.ifBlank { base.boardId },
                updatedAt = System.currentTimeMillis()
            )
            repository.saveNode(updatedNode)
            _uiState.update {
                it.copy(
                    originalNode = updatedNode,
                    hasUnsavedChanges = false,
                    saveBannerMessage = "已保存（正文中的双链已同步更新至图谱关系）"
                )
            }
        }
    }

    fun addRelationFromCurrentNode(
        targetNodeId: String,
        type: RelationType,
        label: String
    ) {
        val currentId = _uiState.value.nodeId
        if (currentId.isBlank() || currentId == targetNodeId) return
        viewModelScope.launch {
            val edge: EdgeData? = repository.createOrUpdateEdge(
                fromNodeId = currentId,
                toNodeId = targetNodeId,
                type = type,
                label = label
            )
            if (edge != null) {
                _uiState.update {
                    it.copy(saveBannerMessage = "已保存「${type.labelCn}」连接")
                }
            }
        }
    }

    fun updateRelation(edge: EdgeData, newType: RelationType, newLabel: String) {
        viewModelScope.launch {
            repository.updateEdge(edge.copy(type = newType, label = newLabel))
            _uiState.update {
                it.copy(saveBannerMessage = "已更新「${newType.labelCn}」关系")
            }
        }
    }

    fun deleteRelation(edgeId: String) {
        viewModelScope.launch {
            repository.deleteEdge(edgeId)
            _uiState.update {
                it.copy(saveBannerMessage = "已移除关系连线")
            }
        }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(saveBannerMessage = null) }
    }
}
