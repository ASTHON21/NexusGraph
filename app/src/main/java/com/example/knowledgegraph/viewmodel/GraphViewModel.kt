package com.example.knowledgegraph.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.GraphLayoutMode
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.repository.KnowledgeRepository
import com.example.knowledgegraph.util.GraphLayoutHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GraphCanvasUiState(
    val scale: Float = 0.95f,
    val panOffset: Offset = Offset.Zero,
    val selectedNodeId: String? = null,
    val selectedEdgeId: String? = null,
    val isConnectMode: Boolean = false,
    val connectSourceNodeId: String? = null,
    val activeRelationType: RelationType = RelationType.RELATED,
    val layoutMode: GraphLayoutMode = GraphLayoutMode.MANUAL,
    val liveNodeOffsets: Map<String, Offset> = emptyMap()
)

/**
 * 关系网画板专用 ViewModel：
 * - 支持围绕双指质心 (Centroid) 的无漂移缩放与画布平移
 * - 实时计算当前画布视野中心对应的世界坐标，供新建节点无碰撞落位
 * - 管理节点实时拖拽与自动落盘持久化
 */
class GraphViewModel(
    private val repository: KnowledgeRepository
) : ViewModel() {

    private val _canvasState = MutableStateFlow(GraphCanvasUiState())
    val canvasState: StateFlow<GraphCanvasUiState> = _canvasState.asStateFlow()

    /**
     * 围绕双指触摸质心 (centroid) 进行平滑缩放与平移，避免缩放时画面跳变偏移
     */
    fun onCentroidTransform(
        centroid: Offset,
        canvasCenter: Offset,
        panDelta: Offset,
        zoomFactor: Float
    ) {
        _canvasState.update { state ->
            val oldScale = state.scale
            val newScale = (oldScale * zoomFactor).coerceIn(0.35f, 2.75f)
            val actualRatio = newScale / oldScale
            val relativeFocus = centroid - canvasCenter - state.panOffset
            val adjustedPan = state.panOffset + panDelta - relativeFocus * (actualRatio - 1f)
            state.copy(
                scale = newScale,
                panOffset = adjustedPan
            )
        }
    }

    fun onPanBy(panDelta: Offset) {
        _canvasState.update { state ->
            state.copy(panOffset = state.panOffset + panDelta)
        }
    }

    /**
     * 计算当前画布视野中心对应的世界坐标
     */
    fun currentViewportCenterWorld(): Offset {
        val state = _canvasState.value
        val safeScale = state.scale.coerceAtLeast(0.2f)
        return Offset(
            x = -state.panOffset.x / safeScale,
            y = -state.panOffset.y / safeScale
        )
    }

    fun zoomIn() {
        _canvasState.update { it.copy(scale = (it.scale * 1.2f).coerceAtMost(2.75f)) }
    }

    fun zoomOut() {
        _canvasState.update { it.copy(scale = (it.scale / 1.2f).coerceAtLeast(0.35f)) }
    }

    fun resetViewport() {
        _canvasState.update {
            it.copy(
                scale = 0.95f,
                panOffset = Offset.Zero
            )
        }
    }

    fun centerOnNode(node: NodeData) {
        _canvasState.update { state ->
            state.copy(
                selectedNodeId = node.id,
                panOffset = Offset(
                    x = -node.x * state.scale,
                    y = -node.y * state.scale
                )
            )
        }
    }

    fun selectNode(nodeId: String?) {
        _canvasState.update {
            it.copy(
                selectedNodeId = nodeId,
                selectedEdgeId = null
            )
        }
    }

    fun selectEdge(edgeId: String?) {
        _canvasState.update {
            it.copy(
                selectedEdgeId = edgeId,
                selectedNodeId = null
            )
        }
    }

    fun toggleConnectMode() {
        _canvasState.update { state ->
            val next = !state.isConnectMode
            state.copy(
                isConnectMode = next,
                connectSourceNodeId = if (next) state.selectedNodeId else null
            )
        }
    }

    fun setActiveRelationType(type: RelationType) {
        _canvasState.update { it.copy(activeRelationType = type) }
    }

    fun onNodeTappedInCanvas(
        tappedNodeId: String,
        onEdgeCreated: ((RelationType) -> Unit)? = null
    ) {
        val state = _canvasState.value
        if (state.isConnectMode) {
            val sourceId = state.connectSourceNodeId
            if (sourceId == null) {
                _canvasState.update {
                    it.copy(
                        connectSourceNodeId = tappedNodeId,
                        selectedNodeId = tappedNodeId
                    )
                }
            } else if (sourceId == tappedNodeId) {
                _canvasState.update { it.copy(connectSourceNodeId = null) }
            } else {
                val relType = state.activeRelationType
                viewModelScope.launch {
                    repository.createOrUpdateEdge(
                        fromNodeId = sourceId,
                        toNodeId = tappedNodeId,
                        type = relType,
                        label = relType.labelCn
                    )
                    _canvasState.update {
                        it.copy(
                            connectSourceNodeId = null,
                            isConnectMode = false,
                            selectedNodeId = tappedNodeId
                        )
                    }
                    onEdgeCreated?.invoke(relType)
                }
            }
        } else {
            selectNode(tappedNodeId)
        }
    }

    fun onNodeDragMove(node: NodeData, worldDelta: Offset) {
        _canvasState.update { state ->
            val currentPos = state.liveNodeOffsets[node.id] ?: Offset(node.x, node.y)
            val nextPos = Offset(
                x = (currentPos.x + worldDelta.x).coerceIn(-1600f, 1600f),
                y = (currentPos.y + worldDelta.y).coerceIn(-1600f, 1600f)
            )
            state.copy(
                selectedNodeId = node.id,
                layoutMode = GraphLayoutMode.MANUAL,
                liveNodeOffsets = state.liveNodeOffsets + (node.id to nextPos)
            )
        }
    }

    fun onNodeDragEnd(nodeId: String) {
        val finalOffset = _canvasState.value.liveNodeOffsets[nodeId] ?: return
        viewModelScope.launch {
            repository.updateNodePosition(nodeId, finalOffset.x, finalOffset.y)
            _canvasState.update { state ->
                state.copy(liveNodeOffsets = state.liveNodeOffsets - nodeId)
            }
        }
    }

    fun applyGraphLayout(
        mode: GraphLayoutMode,
        nodes: List<NodeData>,
        edges: List<EdgeData>
    ) {
        if (nodes.isEmpty()) return
        viewModelScope.launch {
            val newPositions: Map<String, Pair<Float, Float>> = when (mode) {
                GraphLayoutMode.MANUAL -> return@launch
                GraphLayoutMode.CIRCULAR -> GraphLayoutHelper.computeCircularLayout(nodes)
                GraphLayoutMode.RADIAL -> GraphLayoutHelper.computeRadialLayout(
                    nodes = nodes,
                    edges = edges,
                    centerNodeId = _canvasState.value.selectedNodeId
                )
                GraphLayoutMode.FORCE -> GraphLayoutHelper.computeForceDirectedLayout(nodes, edges)
                GraphLayoutMode.GRID -> GraphLayoutHelper.computeGridLayout(nodes)
            }
            repository.updateMultipleNodePositions(newPositions)
            _canvasState.update {
                it.copy(
                    layoutMode = mode,
                    liveNodeOffsets = emptyMap(),
                    panOffset = Offset.Zero
                )
            }
        }
    }
}
