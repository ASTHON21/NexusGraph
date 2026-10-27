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
    val liveNodeOffsets: Map<String, Offset> = emptyMap(),
    val canUndo: Boolean = false
)

/**
 * 关系网画板专用 ViewModel：
 * - 支持围绕双指质心 (Centroid) 的无漂移缩放与画布平移
 * - 支持切换主题或点击复位时自动对焦到可见节点群几何中心
 * - 支持最多 15 步节点坐标撤销栈（覆盖单节点拖拽与算法批量排布）
 */
class GraphViewModel(
    private val repository: KnowledgeRepository
) : ViewModel() {

    private val _canvasState = MutableStateFlow(GraphCanvasUiState())
    val canvasState: StateFlow<GraphCanvasUiState> = _canvasState.asStateFlow()

    private val positionUndoStack = ArrayDeque<Map<String, Pair<Float, Float>>>()
    private val dragStartPositions = mutableMapOf<String, Pair<Float, Float>>()

    private fun pushUndoSnapshot(snapshot: Map<String, Pair<Float, Float>>) {
        if (snapshot.isEmpty()) return
        if (positionUndoStack.size >= 15) {
            positionUndoStack.removeFirst()
        }
        positionUndoStack.addLast(snapshot)
        _canvasState.update { it.copy(canUndo = positionUndoStack.isNotEmpty()) }
    }

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

    /**
     * 将画布视口自动对准指定节点集合的几何中心；若节点集合为空则回到世界原点 (0, 0)
     */
    fun fitViewportToNodes(nodes: List<NodeData>, resetScale: Boolean = true) {
        _canvasState.update { state ->
            val targetScale = if (resetScale) 0.95f else state.scale
            if (nodes.isEmpty()) {
                state.copy(
                    scale = targetScale,
                    panOffset = Offset.Zero
                )
            } else {
                val minX = nodes.minOf { it.x }
                val maxX = nodes.maxOf { it.x }
                val minY = nodes.minOf { it.y }
                val maxY = nodes.maxOf { it.y }
                val centerX = (minX + maxX) / 2f
                val centerY = (minY + maxY) / 2f
                state.copy(
                    scale = targetScale,
                    panOffset = Offset(
                        x = -centerX * targetScale,
                        y = -centerY * targetScale
                    )
                )
            }
        }
    }

    fun resetViewport(nodes: List<NodeData> = emptyList()) {
        fitViewportToNodes(nodes = nodes, resetScale = true)
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
                connectSourceNodeId = if (next) state.selectedNodeId else null,
                selectedEdgeId = null
            )
        }
    }

    fun setActiveRelationType(type: RelationType) {
        _canvasState.update { it.copy(activeRelationType = type) }
    }

    fun onNodeTappedInCanvas(
        tappedNodeId: String,
        onEdgeCreated: ((fromId: String, toId: String, type: RelationType) -> Unit)? = null
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
                    // 保持连线模式不中断，清空起点以便继续连接下一对节点
                    _canvasState.update {
                        it.copy(
                            connectSourceNodeId = null,
                            selectedNodeId = tappedNodeId
                        )
                    }
                    onEdgeCreated?.invoke(sourceId, tappedNodeId, relType)
                }
            }
        } else {
            selectNode(tappedNodeId)
        }
    }

    fun onNodeDragMove(node: NodeData, worldDelta: Offset) {
        if (!dragStartPositions.containsKey(node.id)) {
            dragStartPositions[node.id] = node.x to node.y
        }
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
        val startCoord = dragStartPositions.remove(nodeId)
        if (startCoord != null && (startCoord.first != finalOffset.x || startCoord.second != finalOffset.y)) {
            pushUndoSnapshot(mapOf(nodeId to startCoord))
        }
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
        val previousPositions = nodes.associate { it.id to (it.x to it.y) }
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
            pushUndoSnapshot(previousPositions)
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

    fun undoLastPositionChange() {
        if (positionUndoStack.isEmpty()) return
        val snapshot = positionUndoStack.removeLast()
        _canvasState.update {
            it.copy(
                canUndo = positionUndoStack.isNotEmpty(),
                liveNodeOffsets = emptyMap()
            )
        }
        viewModelScope.launch {
            repository.updateMultipleNodePositions(snapshot)
        }
    }
}
