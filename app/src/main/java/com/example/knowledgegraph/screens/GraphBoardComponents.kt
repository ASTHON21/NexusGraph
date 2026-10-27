package com.example.knowledgegraph.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.knowledgegraph.model.AppUiState
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.GraphLayoutMode
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.NodeUiState
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.util.NodeColorUtil
import com.example.knowledgegraph.viewmodel.GraphCanvasUiState

/**
 * 画板顶部悬浮工具面板：包含主题画板切换栏、连线与新建节点按钮、2字排布算法与显示开关、以及连线模式横幅
 */
@Composable
fun GraphTopControlBar(
    appUiState: AppUiState,
    canvasState: GraphCanvasUiState,
    displayNodes: List<NodeData>,
    activeSelectedNodeId: String?,
    isLandscape: Boolean,
    onSelectBoard: (String?) -> Unit,
    onRequestCreateBoard: () -> Unit,
    onToggleConnectMode: () -> Unit,
    onRequestCreateNode: () -> Unit,
    onApplyLayout: (GraphLayoutMode) -> Unit,
    onToggleCrossBoardNodes: () -> Unit,
    onToggleEdgeLabels: () -> Unit,
    onToggleCanvasGrid: () -> Unit,
    onRequestCreateEdge: (fromNodeId: String?) -> Unit,
    onSelectRelationType: (RelationType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
                            onClick = { onSelectBoard(null) },
                            label = { Text("全部主题") },
                            modifier = Modifier.testTag("board_chip_all")
                        )
                        appUiState.boards.forEach { board ->
                            val boardColor = NodeColorUtil.parseColor(board.colorHex)
                            FilterChip(
                                selected = appUiState.activeBoardId == board.id,
                                onClick = { onSelectBoard(board.id) },
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
                            onClick = onRequestCreateBoard,
                            label = { Text("+新主题") }
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = onToggleConnectMode,
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { onApplyLayout(GraphLayoutMode.FORCE) },
                        label = { Text("力导") }
                    )
                    AssistChip(
                        onClick = { onApplyLayout(GraphLayoutMode.RADIAL) },
                        label = { Text("辐射") }
                    )
                    AssistChip(
                        onClick = { onApplyLayout(GraphLayoutMode.CIRCULAR) },
                        label = { Text("环形") }
                    )
                    AssistChip(
                        onClick = { onApplyLayout(GraphLayoutMode.GRID) },
                        label = { Text("网格") }
                    )
                    if (appUiState.activeBoardId != null) {
                        FilterChip(
                            selected = appUiState.showCrossBoardNodes,
                            onClick = onToggleCrossBoardNodes,
                            label = { Text("跨界") }
                        )
                    }
                    FilterChip(
                        selected = appUiState.showEdgeLabels,
                        onClick = onToggleEdgeLabels,
                        label = { Text("边签") }
                    )
                    FilterChip(
                        selected = appUiState.showCanvasGrid,
                        onClick = onToggleCanvasGrid,
                        label = { Text("底网") }
                    )
                }
            }
        }

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
                        text = if (sourceTitle == null) {
                            "连线模式：1.点选起点"
                        } else {
                            "起点「$sourceTitle」→ 2.点选终点："
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    if (appUiState.nodes.size >= 2) {
                        AssistChip(
                            onClick = {
                                onRequestCreateEdge(
                                    canvasState.connectSourceNodeId ?: activeSelectedNodeId
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AddLink,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = { Text("跨界/弹窗选点") }
                        )
                    }
                    RelationType.entries.forEach { relType ->
                        FilterChip(
                            selected = canvasState.activeRelationType == relType,
                            onClick = { onSelectRelationType(relType) },
                            label = { Text("${relType.symbol} ${relType.labelCn}") }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 画板右侧悬浮工具栏：放大、缩小、视口对焦复位、以及 15 步节点坐标撤销
 */
@Composable
fun GraphZoomAndUndoRail(
    canUndo: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetViewport: () -> Unit,
    onUndoPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilledTonalIconButton(onClick = onZoomIn) {
            Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "放大")
        }
        FilledTonalIconButton(onClick = onZoomOut) {
            Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "缩小")
        }
        FilledTonalIconButton(onClick = onResetViewport) {
            Icon(imageVector = Icons.Default.CenterFocusStrong, contentDescription = "复位")
        }
        FilledTonalIconButton(
            onClick = onUndoPosition,
            enabled = canUndo,
            modifier = Modifier.testTag("canvas_undo_position_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "撤销位置变更"
            )
        }
    }
}

/**
 * 空画布状态引导卡片
 */
@Composable
fun GraphEmptyCanvasCard(
    appUiState: AppUiState,
    onShowCreateBoardDialog: () -> Unit,
    onSelectAllBoards: () -> Unit,
    onRequestCreateNode: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
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
                    FilledTonalButton(onClick = onShowCreateBoardDialog) {
                        Text("+新主题")
                    }
                } else if (appUiState.activeBoardId != null && appUiState.nodes.isNotEmpty()) {
                    FilledTonalButton(onClick = onSelectAllBoards) {
                        Text("查看全部主题")
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

/**
 * 底部悬浮：选中关系边操作卡片
 */
@Composable
fun SelectedEdgeFloatingCard(
    edge: EdgeData,
    fromTitle: String,
    toTitle: String,
    onEditEdge: () -> Unit,
    onDeleteEdge: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
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
                IconButton(onClick = onEditEdge) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑关系",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeleteEdge) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "删除关系",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                }
            }
        }
    }
}

/**
 * 底部悬浮：选中节点检查器卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectedNodeInspectorCard(
    nodeUi: NodeUiState,
    relatedEdgeCount: Int,
    isLandscape: Boolean,
    onSelectTagFilter: (String) -> Unit,
    onOpenNodeDetail: (String) -> Unit,
    onRequestCreateEdge: (String) -> Unit,
    onDeleteNode: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val node = nodeUi.node
    val nodeColor = NodeColorUtil.parseColor(node.color)

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        modifier = modifier
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
                            text = "坐标 (${node.x.toInt()}, ${node.y.toInt()}) · $relatedEdgeCount 关系",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onClose,
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
                            modifier = Modifier.clickable { onSelectTagFilter(tag) }
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

                IconButton(onClick = { onDeleteNode(node.id) }) {
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
