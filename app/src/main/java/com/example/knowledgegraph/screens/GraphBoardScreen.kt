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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgegraph.model.AppUiState
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.GraphLayoutMode
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.NodeUiState
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.util.GraphLayoutHelper
import com.example.knowledgegraph.util.NodeColorUtil
import com.example.knowledgegraph.viewmodel.GraphViewModel
import com.example.knowledgegraph.viewmodel.KnowledgeViewModel

/**
 * 知识图谱 2D 交互式关系网画板页面
 * 核心特性：
 * - 统一多指手势引擎：单指命中节点拖拽、单指空白平移、双指以焦点为中心平滑缩放
 * - 多主题画板切换 + 跨主题节点虚线双环与 ⟦主题名⟧ 标识
 * - 支持直接编辑选中关系边的语义类型与标签
 */
@OptIn(ExperimentalLayoutApi::class)
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

    // 合并实时拖拽中的节点坐标与数据库坐标
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

        // 2D 知识网络画布（统一手势处理：单指节点拖拽 / 单指画布平移 / 双指焦点缩放）
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("knowledge_graph_canvas")
                .pointerInput(Unit) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var draggedNode: NodeData? = GraphLayoutHelper.findNodeAtScreenPosition(
                            screenTouch = down.position,
                            nodes = currentNodesState,
                            canvasCenter = currentCenterState,
                            panOffset = currentPanState,
                            scale = currentScaleState,
                            baseNodeRadiusPx = baseNodeRadiusPx
                        )
                        var isDraggingNode = false
                        var totalPanDistance = 0f

                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                // 双指缩放与平移：若之前命中了节点，立即结束节点拖拽转为视口变换
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

            // 1. 绘制无限感点阵网格与当前主题画板微光边界
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

            // 2. 绘制节点间的有向语义关系连线
            val scaledRadius = baseNodeRadiusPx * scale
            appUiState.canvasEdges.forEach { edge ->
                val fromNodeUi = nodeUiMap[edge.fromNodeId] ?: return@forEach
                val toNodeUi = nodeUiMap[edge.toNodeId] ?: return@forEach
                val fromNode = fromNodeUi.node
                val toNode = toNodeUi.node

                val startPos = GraphLayoutHelper.worldToScreen(fromNode.x, fromNode.y, center, pan, scale)
                val endPos = GraphLayoutHelper.worldToScreen(toNode.x, toNode.y, center, pan, scale)

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

                // 跨主题边或冲突/引用边采用虚线点划区分
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

                // 绘制方向箭头
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

                // 绘制连线中点语义标签徽章
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

            // 3. 绘制所有可见节点（原生节点实线圆环 vs 跨主题外部节点虚线双环 + ⟦主题名⟧ 标识）
            displayNodeUiList.forEach { nodeUi ->
                val node = nodeUi.node
                val screenPos = GraphLayoutHelper.worldToScreen(node.x, node.y, center, pan, scale)
                val nodeColor = NodeColorUtil.parseColor(node.color)
                val boardColor = NodeColorUtil.parseColor(nodeUi.boardColorHex)
                val isSelected = node.id == activeSelectedNodeId
                val isConnectSource = canvasState.isConnectMode && canvasState.connectSourceNodeId == node.id

                // 选中或连线起点脉冲外圈光晕
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

                // 若为跨主题关联节点，绘制所属主题色的虚线外环，确保直观区分
                if (nodeUi.isCrossBoard) {
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

                // 节点主圆盘
                drawCircle(
                    color = if (nodeUi.isCrossBoard) nodeColor.copy(alpha = 0.72f) else nodeColor,
                    radius = scaledRadius,
                    center = screenPos
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = scaledRadius,
                    center = screenPos,
                    style = Stroke(width = if (isSelected) 4f else 2f)
                )

                // 节点中心首字标识
                titlePaint.textSize = (14f * scale).coerceIn(20f, 36f)
                titlePaint.color = Color.White.toArgb()
                val shortGlyph = node.title.trim().take(2)
                drawContext.canvas.nativeCanvas.drawText(
                    shortGlyph,
                    screenPos.x,
                    screenPos.y + titlePaint.textSize / 3f,
                    titlePaint
                )

                // 节点底部标题胶囊（跨主题节点自动带 ⟦主题名⟧ 前缀）
                titlePaint.textSize = (12.5f * scale).coerceIn(20f, 32f)
                titlePaint.color = labelTextColorArgb
                val baseTitle = if (node.title.length > 12) {
                    node.title.take(11) + "…"
                } else {
                    node.title
                }
                val displayTitle = if (nodeUi.isCrossBoard) {
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
                if (isSelected || nodeUi.isCrossBoard) {
                    drawRoundRect(
                        color = if (nodeUi.isCrossBoard) boardColor else nodeColor,
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

        // 空画布提示卡（当当前主题或全域尚无节点时展示）
        if (displayNodeUiList.isEmpty()) {
            OutlinedCard(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = if (appUiState.boards.isEmpty()) {
                            "当前尚无主题画板与节点"
                        } else {
                            "「${appUiState.activeBoard?.name ?: "全部主题"}」暂无知识节点"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "新建节点将在当前视口中心无重叠排布，双击节点即可进入 Markdown 编辑器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (appUiState.boards.isEmpty()) {
                            FilledTonalButton(onClick = { showCreateBoardDialog = true }) {
                                Text("+新主题")
                            }
                        }
                        Button(onClick = onRequestCreateNode) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新节点")
                        }
                    }
                }
            }
        }

        // 顶部悬浮控制面板（自适应横屏单行紧凑模式与竖屏双行模式）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 10.dp, vertical = if (isLandscape) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = if (isLandscape) 4.dp else 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
                ) {
                    // 第一行：主题画板快速切换胶囊 + 连线 & 新节点按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = appUiState.activeBoardId == null,
                                onClick = { knowledgeViewModel.selectActiveBoard(null) },
                                label = { Text("全部主题") }
                            )
                            appUiState.boards.forEach { board ->
                                val boardColor = NodeColorUtil.parseColor(board.colorHex)
                                FilterChip(
                                    selected = appUiState.activeBoardId == board.id,
                                    onClick = { knowledgeViewModel.selectActiveBoard(board.id) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(boardColor)
                                        )
                                    },
                                    label = { Text(board.name) }
                                )
                            }
                            AssistChip(
                                onClick = { showCreateBoardDialog = true },
                                label = { Text("+新主题") }
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = { graphViewModel.toggleConnectMode() },
                                modifier = Modifier.testTag("toggle_connect_mode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "连线",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(if (canvasState.isConnectMode) "退出" else "连线")
                            }
                            Button(
                                onClick = onRequestCreateNode,
                                modifier = Modifier.testTag("canvas_add_node_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "新节点",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("新节点")
                            }
                        }
                    }

                    // 第二行：2字极简排布控制与显示芯片
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {
                                graphViewModel.applyGraphLayout(
                                    GraphLayoutMode.FORCE,
                                    displayNodes,
                                    appUiState.canvasEdges
                                )
                            },
                            label = { Text("力导") }
                        )
                        AssistChip(
                            onClick = {
                                graphViewModel.applyGraphLayout(
                                    GraphLayoutMode.RADIAL,
                                    displayNodes,
                                    appUiState.canvasEdges
                                )
                            },
                            label = { Text("辐射") }
                        )
                        AssistChip(
                            onClick = {
                                graphViewModel.applyGraphLayout(
                                    GraphLayoutMode.CIRCULAR,
                                    displayNodes,
                                    appUiState.canvasEdges
                                )
                            },
                            label = { Text("环形") }
                        )
                        AssistChip(
                            onClick = {
                                graphViewModel.applyGraphLayout(
                                    GraphLayoutMode.GRID,
                                    displayNodes,
                                    appUiState.canvasEdges
                                )
                            },
                            label = { Text("网格") }
                        )
                        if (appUiState.activeBoardId != null) {
                            FilterChip(
                                selected = appUiState.showCrossBoardNodes,
                                onClick = { knowledgeViewModel.toggleShowCrossBoardNodes() },
                                label = { Text("跨界") }
                            )
                        }
                        FilterChip(
                            selected = appUiState.showEdgeLabels,
                            onClick = { knowledgeViewModel.toggleShowEdgeLabels() },
                            label = { Text("边签") }
                        )
                        FilterChip(
                            selected = appUiState.showCanvasGrid,
                            onClick = { knowledgeViewModel.toggleShowCanvasGrid() },
                            label = { Text("底网") }
                        )
                    }
                }
            }

            // 连线模式提示条
            AnimatedVisibility(visible = canvasState.isConnectMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sourceTitle = displayNodes
                            .firstOrNull { it.id == canvasState.connectSourceNodeId }
                            ?.title
                        Text(
                            text = if (sourceTitle == null) "点选起点：" else "起点「$sourceTitle」→点选终点：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        RelationType.entries.forEach { relType ->
                            FilterChip(
                                selected = canvasState.activeRelationType == relType,
                                onClick = { graphViewModel.setActiveRelationType(relType) },
                                label = { Text("${relType.symbol} ${relType.labelCn}") }
                            )
                        }
                    }
                }
            }
        }

        // 右侧悬浮缩放与复位工具列
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalIconButton(onClick = { graphViewModel.zoomIn() }) {
                Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "放大")
            }
            FilledTonalIconButton(onClick = { graphViewModel.zoomOut() }) {
                Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "缩小")
            }
            FilledTonalIconButton(onClick = { graphViewModel.resetViewport() }) {
                Icon(imageVector = Icons.Default.CenterFocusStrong, contentDescription = "复位")
            }
        }

        // 底部悬浮：选中关系边操作卡（支持编辑关系类型与标签，或删除关系）
        AnimatedVisibility(
            visible = selectedEdge != null && selectedNodeUi == null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            selectedEdge?.let { edge ->
                val fromTitle = nodeMap[edge.fromNodeId]?.title ?: "起点"
                val toTitle = nodeMap[edge.toNodeId]?.title ?: "终点"
                ElevatedCard(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${edge.type.symbol} ${edge.displayLabel} (${edge.type.name})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NodeColorUtil.relationColor(edge.type)
                            )
                            Text(
                                text = "$fromTitle  ──►  $toTitle",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row {
                            IconButton(onClick = { editingEdge = edge }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "编辑关系",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    knowledgeViewModel.deleteEdge(edge.id)
                                    graphViewModel.selectEdge(null)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "删除关系",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { graphViewModel.selectEdge(null) }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                            }
                        }
                    }
                }
            }
        }

        // 底部悬浮：选中节点检查器卡片（横竖屏自适应宽度限制）
        AnimatedVisibility(
            visible = selectedNodeUi != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = if (isLandscape) 8.dp else 12.dp)
        ) {
            selectedNodeUi?.let { nodeUi ->
                val node = nodeUi.node
                val nodeColor = NodeColorUtil.parseColor(node.color)
                val relatedEdges = appUiState.edges.filter {
                    it.fromNodeId == node.id || it.toNodeId == node.id
                }

                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .widthIn(max = 540.dp)
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = nodeColor.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .testTag("selected_node_inspector_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isLandscape) 12.dp else 14.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(nodeColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = node.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            color = NodeColorUtil.parseColor(nodeUi.boardColorHex)
                                                .copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (nodeUi.isCrossBoard) {
                                                    "跨界·${nodeUi.boardName}"
                                                } else {
                                                    nodeUi.boardName
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NodeColorUtil.parseColor(nodeUi.boardColorHex),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "坐标 (${node.x.toInt()}, ${node.y.toInt()}) · ${relatedEdges.size} 关系",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    graphViewModel.selectNode(null)
                                    knowledgeViewModel.selectNode(null)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (!isLandscape && node.tagList.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                node.tagList.forEach { tag ->
                                    Surface(
                                        color = nodeColor.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable {
                                            knowledgeViewModel.selectTagFilter(tag)
                                        }
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = nodeColor,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = node.summarySnippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isLandscape) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onOpenNodeDetail(node.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_node_editor_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("打开文档")
                            }

                            FilledTonalButton(
                                onClick = { onRequestCreateEdge(node.id) },
                                modifier = Modifier.testTag("add_relation_from_inspector_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLink,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("新建关系")
                            }

                            IconButton(
                                onClick = {
                                    knowledgeViewModel.deleteNode(node.id)
                                    graphViewModel.selectNode(null)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateBoardDialog) {
        TopicBoardDialog(
            initialBoard = null,
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
