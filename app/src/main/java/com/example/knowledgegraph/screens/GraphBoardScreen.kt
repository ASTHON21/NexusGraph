package com.example.knowledgegraph.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgegraph.model.AppUiState
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.NodeUiState
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.util.GraphLayoutHelper
import com.example.knowledgegraph.util.NodeColorUtil
import com.example.knowledgegraph.viewmodel.GraphViewModel
import com.example.knowledgegraph.viewmodel.KnowledgeViewModel
import kotlin.math.max
import kotlin.math.min

/**
 * 知识图谱 2D 交互式关系网画板页面
 * 核心特性：
 * - 统一多指手势引擎：单指命中节点拖拽、单指空白平移、双指以焦点为中心平滑缩放
 * - 视口裁剪优化（Viewport Frustum Culling）：自动跳过屏幕可视区外的节点与连线绘制
 * - 切换「全部主题」或特定主题画板时自动将视口对准当前可见节点群几何中心
 * - 支持 15 步节点坐标拖拽与排布撤销
 */
@Composable
fun GraphBoardScreen(
    appUiState: AppUiState,
    knowledgeViewModel: KnowledgeViewModel,
    graphViewModel: GraphViewModel,
    onOpenNodeDetail: (String) -> Unit,
    onRequestCreateNode: () -> Unit,
    onRequestCreateEdge: (fromNodeId: String?) -> Unit
) {
    val canvasState by graphViewModel.canvasState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val baseNodeRadiusPx = with(density) { 29.dp.toPx() }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val canvasCenter = remember(canvasSize) {
        Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    }

    var showCreateBoardDialog by remember { mutableStateOf(false) }
    var editingEdge by remember { mutableStateOf<EdgeData?>(null) }

    val displayNodeUiList: List<NodeUiState> = remember(
        appUiState.canvasNodeUiList,
        canvasState.liveNodeOffsets
    ) {
        appUiState.canvasNodeUiList.map { nodeUi ->
            val live = canvasState.liveNodeOffsets[nodeUi.node.id]
            if (live != null) {
                nodeUi.copy(node = nodeUi.node.copy(x = live.x, y = live.y))
            } else {
                nodeUi
            }
        }
    }
    val displayNodes: List<NodeData> = remember(displayNodeUiList) {
        displayNodeUiList.map { it.node }
    }
    val nodeUiMap = remember(displayNodeUiList) {
        displayNodeUiList.associateBy { it.node.id }
    }
    val nodeMap = remember(displayNodes) {
        displayNodes.associateBy { it.id }
    }

    LaunchedEffect(appUiState.activeBoardId) {
        val targetNodes = appUiState.canvasNodeUiList.map { it.node }
        graphViewModel.fitViewportToNodes(targetNodes, resetScale = false)
    }

    val activeSelectedNodeId = canvasState.selectedNodeId ?: appUiState.selectedNodeId
    val selectedNodeUi: NodeUiState? = remember(displayNodeUiList, activeSelectedNodeId) {
        displayNodeUiList.firstOrNull { it.node.id == activeSelectedNodeId }
    }
    val selectedEdge: EdgeData? = remember(appUiState.canvasEdges, canvasState.selectedEdgeId) {
        appUiState.canvasEdges.firstOrNull { it.id == canvasState.selectedEdgeId }
    }

    val currentNodesState by rememberUpdatedState(displayNodes)
    val currentEdgesState by rememberUpdatedState(appUiState.canvasEdges)
    val currentNodeMapState by rememberUpdatedState(nodeMap)
    val currentCenterState by rememberUpdatedState(canvasCenter)
    val currentScaleState by rememberUpdatedState(canvasState.scale)
    val currentPanState by rememberUpdatedState(canvasState.panOffset)
    val isConnectModeState by rememberUpdatedState(canvasState.isConnectMode)

    val isDark = appUiState.isDarkTheme
    val canvasBgColor = if (isDark) Color(0xFF080D1A) else Color(0xFFF1F5F9)
    val gridDotColor = if (isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val labelTextColorArgb = if (isDark) Color(0xFFF8FAFC).toArgb() else Color(0xFF0F172A).toArgb()
    val subTextColorArgb = if (isDark) Color(0xFF94A3B8).toArgb() else Color(0xFF475569).toArgb()
    val pillBgColor = if (isDark) Color(0xDD0F172A) else Color(0xEEFFFFFF)

    val titlePaint = remember(labelTextColorArgb) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelTextColorArgb
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val badgePaint = remember(subTextColorArgb) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColorArgb
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBgColor)
            .onSizeChanged { canvasSize = it }
    ) {
        val isLandscape = maxWidth > maxHeight

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("knowledge_graph_canvas")
                .pointerInput(Unit) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var draggedNode: NodeData? = if (isConnectModeState) {
                            null
                        } else {
                            GraphLayoutHelper.findNodeAtScreenPosition(
                                screenTouch = down.position,
                                nodes = currentNodesState,
                                canvasCenter = currentCenterState,
                                panOffset = currentPanState,
                                scale = currentScaleState,
                                baseNodeRadiusPx = baseNodeRadiusPx
                            )
                        }
                        var isDraggingNode = false
                        var totalPanDistance = 0f

                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                if (isDraggingNode && draggedNode != null) {
                                    graphViewModel.onNodeDragEnd(draggedNode!!.id)
                                }
                                draggedNode = null
                                isDraggingNode = false

                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)

                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    graphViewModel.onCentroidTransform(
                                        centroid = if (centroid == Offset.Unspecified) currentCenterState else centroid,
                                        canvasCenter = currentCenterState,
                                        panDelta = panChange,
                                        zoomFactor = zoomChange
                                    )
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            change.consume()
                                        }
                                    }
                                }
                            } else if (pressedCount == 1) {
                                val panChange = event.calculatePan()
                                if (panChange != Offset.Zero) {
                                    totalPanDistance += panChange.getDistance()
                                    if (totalPanDistance > touchSlop || isDraggingNode) {
                                        val target = draggedNode
                                        if (target != null) {
                                            if (!isDraggingNode) {
                                                isDraggingNode = true
                                                knowledgeViewModel.selectNode(target.id)
                                                graphViewModel.selectNode(target.id)
                                            }
                                            val worldDelta = Offset(
                                                x = panChange.x / currentScaleState.coerceAtLeast(0.2f),
                                                y = panChange.y / currentScaleState.coerceAtLeast(0.2f)
                                            )
                                            val latestNode = currentNodesState.firstOrNull { it.id == target.id } ?: target
                                            graphViewModel.onNodeDragMove(latestNode, worldDelta)
                                        } else {
                                            graphViewModel.onPanBy(panChange)
                                        }
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) {
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDraggingNode && draggedNode != null) {
                            graphViewModel.onNodeDragEnd(draggedNode!!.id)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val tappedNode = GraphLayoutHelper.findNodeAtScreenPosition(
                                screenTouch = tapOffset,
                                nodes = currentNodesState,
                                canvasCenter = currentCenterState,
                                panOffset = currentPanState,
                                scale = currentScaleState,
                                baseNodeRadiusPx = baseNodeRadiusPx
                            )
                            if (tappedNode != null) {
                                knowledgeViewModel.selectNode(tappedNode.id)
                                graphViewModel.onNodeTappedInCanvas(tappedNode.id)
                            } else {
                                val tappedEdge = GraphLayoutHelper.findEdgeAtScreenPosition(
                                    screenTouch = tapOffset,
                                    edges = currentEdgesState,
                                    nodeMap = currentNodeMapState,
                                    canvasCenter = currentCenterState,
                                    panOffset = currentPanState,
                                    scale = currentScaleState
                                )
                                if (tappedEdge != null) {
                                    graphViewModel.selectEdge(tappedEdge.id)
                                } else {
                                    graphViewModel.selectNode(null)
                                }
                            }
                        },
                        onDoubleTap = { tapOffset ->
                            val tappedNode = GraphLayoutHelper.findNodeAtScreenPosition(
                                screenTouch = tapOffset,
                                nodes = currentNodesState,
                                canvasCenter = currentCenterState,
                                panOffset = currentPanState,
                                scale = currentScaleState,
                                baseNodeRadiusPx = baseNodeRadiusPx
                            )
                            if (tappedNode != null) {
                                knowledgeViewModel.selectNode(tappedNode.id)
                                onOpenNodeDetail(tappedNode.id)
                            }
                        }
                    )
                }
        ) {
            val scale = canvasState.scale
            val pan = canvasState.panOffset
            val center = Offset(size.width / 2f, size.height / 2f)
            val cullMargin = 220f

            // 1. 绘制无限感点阵网格
            if (appUiState.showCanvasGrid) {
                val gridStep = (80f * scale).coerceIn(36f, 180f)
                val startX = ((center.x + pan.x) % gridStep + gridStep) % gridStep
                val startY = ((center.y + pan.y) % gridStep + gridStep) % gridStep

                var gx = startX
                while (gx < size.width) {
                    var gy = startY
                    while (gy < size.height) {
                        drawCircle(
                            color = gridDotColor,
                            radius = 2.2f,
                            center = Offset(gx, gy)
                        )
                        gy += gridStep
                    }
                    gx += gridStep
                }

                val originScreen = GraphLayoutHelper.worldToScreen(0f, 0f, center, pan, scale)
                drawLine(
                    color = gridDotColor.copy(alpha = 0.55f),
                    start = Offset(0f, originScreen.y),
                    end = Offset(size.width, originScreen.y),
                    strokeWidth = 1f
                )
                drawLine(
                    color = gridDotColor.copy(alpha = 0.55f),
                    start = Offset(originScreen.x, 0f),
                    end = Offset(originScreen.x, size.height),
                    strokeWidth = 1f
                )
            }

            // 2. 绘制节点间的有向语义关系连线（带视口包围盒裁剪）
            val scaledRadius = baseNodeRadiusPx * scale
            appUiState.canvasEdges.forEach { edge ->
                val fromNodeUi = nodeUiMap[edge.fromNodeId] ?: return@forEach
                val toNodeUi = nodeUiMap[edge.toNodeId] ?: return@forEach
                val fromNode = fromNodeUi.node
                val toNode = toNodeUi.node

                val startPos = GraphLayoutHelper.worldToScreen(fromNode.x, fromNode.y, center, pan, scale)
                val endPos = GraphLayoutHelper.worldToScreen(toNode.x, toNode.y, center, pan, scale)

                val minX = min(startPos.x, endPos.x)
                val maxX = max(startPos.x, endPos.x)
                val minY = min(startPos.y, endPos.y)
                val maxY = max(startPos.y, endPos.y)
                if (maxX < -cullMargin || minX > size.width + cullMargin ||
                    maxY < -cullMargin || minY > size.height + cullMargin
                ) {
                    return@forEach
                }

                val isEdgeSelected = canvasState.selectedEdgeId == edge.id
                val isConnectedToSelected = activeSelectedNodeId != null &&
                    (edge.fromNodeId == activeSelectedNodeId || edge.toNodeId == activeSelectedNodeId)
                val isCrossBoardEdge = fromNode.boardId != toNode.boardId

                val baseRelColor = NodeColorUtil.relationColor(edge.type)
                val lineColor = when {
                    isEdgeSelected -> baseRelColor
                    isConnectedToSelected -> baseRelColor.copy(alpha = 0.95f)
                    activeSelectedNodeId != null -> baseRelColor.copy(alpha = 0.32f)
                    else -> baseRelColor.copy(alpha = 0.75f)
                }

                val strokeW = when {
                    isEdgeSelected -> 7f
                    isConnectedToSelected -> 5f
                    else -> 3.2f
                }

                val pathEffect = when {
                    isCrossBoardEdge -> PathEffect.dashPathEffect(floatArrayOf(22f, 12f), 0f)
                    edge.type == RelationType.CONTRADICTS || edge.type == RelationType.REFERENCES ->
                        PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    else -> null
                }

                drawLine(
                    color = lineColor,
                    start = startPos,
                    end = endPos,
                    strokeWidth = strokeW,
                    pathEffect = pathEffect
                )

                val arrowPoints = GraphLayoutHelper.computeArrowHeadPoints(
                    start = startPos,
                    end = endPos,
                    targetRadius = scaledRadius + 6f,
                    arrowLength = (20f * scale).coerceIn(12f, 28f)
                )
                if (arrowPoints != null) {
                    val arrowPath = Path().apply {
                        moveTo(arrowPoints.first.x, arrowPoints.first.y)
                        lineTo(arrowPoints.second.x, arrowPoints.second.y)
                        lineTo(arrowPoints.third.x, arrowPoints.third.y)
                        close()
                    }
                    drawPath(path = arrowPath, color = lineColor)
                }

                if (appUiState.showEdgeLabels && scale >= 0.55f) {
                    val mid = Offset(
                        x = (startPos.x + endPos.x) / 2f,
                        y = (startPos.y + endPos.y) / 2f
                    )
                    val prefix = if (isCrossBoardEdge) "跨·" else ""
                    val labelText = "${edge.type.symbol} $prefix${edge.displayLabel}"
                    badgePaint.textSize = (11.5f * scale).coerceIn(18f, 28f)
                    badgePaint.color = lineColor.toArgb()

                    val textW = badgePaint.measureText(labelText) + 22f
                    val textH = badgePaint.textSize + 14f

                    drawRoundRect(
                        color = pillBgColor,
                        topLeft = Offset(mid.x - textW / 2f, mid.y - textH / 2f),
                        size = Size(textW, textH),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                    drawRoundRect(
                        color = lineColor.copy(alpha = 0.6f),
                        topLeft = Offset(mid.x - textW / 2f, mid.y - textH / 2f),
                        size = Size(textW, textH),
                        cornerRadius = CornerRadius(12f, 12f),
                        style = Stroke(width = 1.5f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        mid.x,
                        mid.y + badgePaint.textSize / 3f,
                        badgePaint
                    )
                }
            }

            // 3. 绘制所有可见节点（带视口裁剪）
            displayNodeUiList.forEach { nodeUi ->
                val node = nodeUi.node
                val screenPos = GraphLayoutHelper.worldToScreen(node.x, node.y, center, pan, scale)
                if (screenPos.x < -cullMargin || screenPos.x > size.width + cullMargin ||
                    screenPos.y < -cullMargin || screenPos.y > size.height + cullMargin
                ) {
                    return@forEach
                }

                val nodeColor = NodeColorUtil.parseColor(node.color)
                val boardColor = NodeColorUtil.parseColor(nodeUi.boardColorHex)
                val isSelected = node.id == activeSelectedNodeId
                val isConnectSource = canvasState.isConnectMode && canvasState.connectSourceNodeId == node.id

                if (isSelected || isConnectSource) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                nodeColor.copy(alpha = 0.45f),
                                nodeColor.copy(alpha = 0.0f)
                            ),
                            center = screenPos,
                            radius = scaledRadius * 2.1f
                        ),
                        radius = scaledRadius * 2.1f,
                        center = screenPos
                    )
                    drawCircle(
                        color = if (isConnectSource) Color(0xFF38BDF8) else nodeColor,
                        radius = scaledRadius + 7f,
                        center = screenPos,
                        style = Stroke(width = 4f)
                    )
                }

                if (nodeUi.isCrossBoard || appUiState.activeBoardId == null) {
                    drawCircle(
                        color = boardColor,
                        radius = scaledRadius + 8f,
                        center = screenPos,
                        style = Stroke(
                            width = 3.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    )
                }

                drawCircle(
                    color = if (nodeUi.isCrossBoard) nodeColor.copy(alpha = 0.78f) else nodeColor,
                    radius = scaledRadius,
                    center = screenPos
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = scaledRadius,
                    center = screenPos,
                    style = Stroke(width = if (isSelected) 4f else 2f)
                )

                titlePaint.textSize = (14f * scale).coerceIn(20f, 36f)
                titlePaint.color = Color.White.toArgb()
                val shortGlyph = node.title.trim().take(2)
                drawContext.canvas.nativeCanvas.drawText(
                    shortGlyph,
                    screenPos.x,
                    screenPos.y + titlePaint.textSize / 3f,
                    titlePaint
                )

                titlePaint.textSize = (12.5f * scale).coerceIn(20f, 32f)
                titlePaint.color = labelTextColorArgb
                val baseTitle = if (node.title.length > 12) {
                    node.title.take(11) + "…"
                } else {
                    node.title
                }
                val showBoardBadge = nodeUi.isCrossBoard || appUiState.activeBoardId == null
                val displayTitle = if (showBoardBadge) {
                    "⟦${nodeUi.boardName}⟧ $baseTitle"
                } else {
                    baseTitle
                }
                val labelW = titlePaint.measureText(displayTitle) + 26f
                val labelH = titlePaint.textSize + 15f
                val labelTop = screenPos.y + scaledRadius + 11f

                drawRoundRect(
                    color = pillBgColor,
                    topLeft = Offset(screenPos.x - labelW / 2f, labelTop),
                    size = Size(labelW, labelH),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                if (isSelected || showBoardBadge) {
                    drawRoundRect(
                        color = if (showBoardBadge) boardColor else nodeColor,
                        topLeft = Offset(screenPos.x - labelW / 2f, labelTop),
                        size = Size(labelW, labelH),
                        cornerRadius = CornerRadius(14f, 14f),
                        style = Stroke(
                            width = if (isSelected) 2.5f else 1.5f,
                            pathEffect = if (nodeUi.isCrossBoard && !isSelected) {
                                PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            } else {
                                null
                            }
                        )
                    )
                }
                drawContext.canvas.nativeCanvas.drawText(
                    displayTitle,
                    screenPos.x,
                    labelTop + labelH / 2f + titlePaint.textSize / 3f,
                    titlePaint
                )
            }
        }

        if (displayNodeUiList.isEmpty()) {
            GraphEmptyCanvasCard(
                appUiState = appUiState,
                onShowCreateBoardDialog = { showCreateBoardDialog = true },
                onSelectAllBoards = { knowledgeViewModel.selectActiveBoard(null) },
                onRequestCreateNode = onRequestCreateNode,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        GraphTopControlBar(
            appUiState = appUiState,
            canvasState = canvasState,
            displayNodes = displayNodes,
            activeSelectedNodeId = activeSelectedNodeId,
            isLandscape = isLandscape,
            onSelectBoard = { knowledgeViewModel.selectActiveBoard(it) },
            onRequestCreateBoard = { showCreateBoardDialog = true },
            onToggleConnectMode = { graphViewModel.toggleConnectMode() },
            onRequestCreateNode = onRequestCreateNode,
            onApplyLayout = { mode ->
                graphViewModel.applyGraphLayout(mode, displayNodes, appUiState.canvasEdges)
            },
            onToggleCrossBoardNodes = { knowledgeViewModel.toggleShowCrossBoardNodes() },
            onToggleEdgeLabels = { knowledgeViewModel.toggleShowEdgeLabels() },
            onToggleCanvasGrid = { knowledgeViewModel.toggleShowCanvasGrid() },
            onRequestCreateEdge = onRequestCreateEdge,
            onSelectRelationType = { graphViewModel.setActiveRelationType(it) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        GraphZoomAndUndoRail(
            canUndo = canvasState.canUndo,
            onZoomIn = { graphViewModel.zoomIn() },
            onZoomOut = { graphViewModel.zoomOut() },
            onResetViewport = { graphViewModel.resetViewport(displayNodes) },
            onUndoPosition = { graphViewModel.undoLastPositionChange() },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        AnimatedVisibility(
            visible = selectedEdge != null && selectedNodeUi == null && !canvasState.isConnectMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            selectedEdge?.let { edge ->
                SelectedEdgeFloatingCard(
                    edge = edge,
                    fromTitle = nodeMap[edge.fromNodeId]?.title ?: "起点",
                    toTitle = nodeMap[edge.toNodeId]?.title ?: "终点",
                    onEditEdge = { editingEdge = edge },
                    onDeleteEdge = {
                        knowledgeViewModel.deleteEdge(edge.id)
                        graphViewModel.selectEdge(null)
                    },
                    onClose = { graphViewModel.selectEdge(null) }
                )
            }
        }

        AnimatedVisibility(
            visible = selectedNodeUi != null && !canvasState.isConnectMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = if (isLandscape) 8.dp else 12.dp)
        ) {
            selectedNodeUi?.let { nodeUi ->
                SelectedNodeInspectorCard(
                    nodeUi = nodeUi,
                    relatedEdgeCount = nodeUi.incomingCount + nodeUi.outgoingCount,
                    isLandscape = isLandscape,
                    onSelectTagFilter = { knowledgeViewModel.selectTagFilter(it) },
                    onOpenNodeDetail = onOpenNodeDetail,
                    onRequestCreateEdge = onRequestCreateEdge,
                    onDeleteNode = { id ->
                        knowledgeViewModel.deleteNode(id)
                        graphViewModel.selectNode(null)
                    },
                    onClose = {
                        graphViewModel.selectNode(null)
                        knowledgeViewModel.selectNode(null)
                    }
                )
            }
        }
    }

    if (showCreateBoardDialog) {
        TopicBoardDialog(
            initialBoard = null,
            existingBoardCount = appUiState.boards.size,
            onDismiss = { showCreateBoardDialog = false },
            onConfirm = { name, desc, colorHex ->
                showCreateBoardDialog = false
                knowledgeViewModel.createBoard(name, desc, colorHex)
            }
        )
    }

    editingEdge?.let { targetEdge ->
        val fromTitle = nodeMap[targetEdge.fromNodeId]?.title ?: "起点"
        val toTitle = nodeMap[targetEdge.toNodeId]?.title ?: "终点"
        EditEdgeDialog(
            edge = targetEdge,
            fromNodeTitle = fromTitle,
            toNodeTitle = toTitle,
            onDismiss = { editingEdge = null },
            onConfirm = { newType, newLabel ->
                knowledgeViewModel.updateEdge(
                    targetEdge.copy(type = newType, label = newLabel)
                )
                editingEdge = null
            }
        )
    }
}
