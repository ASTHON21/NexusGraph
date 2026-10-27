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
import kotlin.math.min

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
 * - 支持根据节点群包围盒（Bounding Box）自动计算最佳缩放比例与居中偏移
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
     * 根据节点集合的包围盒自动计算最佳缩放比例与中心平移，使全图节点完整居中呈现
     */
    fun fitViewportToNodes(
        nodes: List<NodeData>,
        resetScale: Boolean = true,
        viewportWidthWorld: Float = 860f,
        viewportHeightWorld: Float = 1050f
    ) {
        fitViewportToCoordinates(
            coords = nodes.map { it.x to it.y },
            resetScale = resetScale,
            viewportWidthWorld = viewportWidthWorld,
            viewportHeightWorld = viewportHeightWorld
        )
    }

    private fun fitViewportToCoordinates(
        coords: List<Pair<Float, Float>>,
        resetScale: Boolean = true,
        viewportWidthWorld: Float = 860f,
        viewportHeightWorld: Float = 1050f
    ) {
        _canvasState.update { state ->
            if (coords.isEmpty()) {
                state.copy(
                    scale = if (resetScale) 0.95f else state.scale,
                    panOffset = Offset.Zero
                )
            } else {
                val minX = coords.minOf { it.first }
                val maxX = coords.maxOf { it.first }
                val minY = coords.minOf { it.second }
                val maxY = coords.maxOf { it.second }
                val centerX = (minX + maxX) / 2f
                val centerY = (minY + maxY) / 2f

                val targetScale = if (!resetScale) {
                    state.scale
                } else if (coords.size == 1) {
                    1.0f
                } else {
                    val spanX = (maxX - minX) + 340f
                    val spanY = (maxY - minY) + 360f
                    val scaleX = viewportWidthWorld / spanX
                    val scaleY = viewportHeightWorld / spanY
                    min(scaleX, scaleY).coerceIn(0.48f, 1.05f)
                }

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
            val targetScale = state.scale.coerceIn(0.8f, 1.25f)
            state.copy(
                selectedNodeId = node.id,
                scale = targetScale,
                panOffset = Offset(
                    x = -node.x * targetScale,
                    y = -node.y * targetScale
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
                x = (currentPos.x + worldDelta.x).coerceIn(-2800f, 2800f),
                y = (currentPos.y + worldDelta.y).coerceIn(-2800f, 2800f)
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
                    liveNodeOffsets = emptyMap()
                )
            }
            fitViewportToCoordinates(
                coords = newPositions.values.toList(),
                resetScale = true
            )
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
            fitViewportToCoordinates(
                coords = snapshot.values.toList(),
                resetScale = false
            )
        }
    }
}
