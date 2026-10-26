package com.example.knowledgegraph.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.knowledgegraph.model.AppUiState
import com.example.knowledgegraph.model.NodeUiState
import com.example.knowledgegraph.util.DateTimeFormatter
import com.example.knowledgegraph.util.NodeColorUtil
import com.example.knowledgegraph.viewmodel.KnowledgeViewModel

private enum class NodeSortOption(val label: String) {
    UPDATED_DESC("更新"),
    DEGREE_DESC("连接"),
    TITLE_ASC("标题")
}

/**
 * 知识节点同步列表页 (NodeListScreen)
 * 支持按主题画板、标签、排序规则查看节点，并展示所属主题标识与跨主题关联状态
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeListScreen(
    appUiState: AppUiState,
    knowledgeViewModel: KnowledgeViewModel,
    onOpenNodeDetail: (String) -> Unit,
    onLocateNodeInGraph: (String) -> Unit,
    onRequestCreateNode: () -> Unit
) {
    var sortOption by remember { mutableStateOf(NodeSortOption.UPDATED_DESC) }

    val filteredAndSorted: List<NodeUiState> = remember(
        appUiState.canvasNodeUiList,
        sortOption
    ) {
        val base = appUiState.canvasNodeUiList
        when (sortOption) {
            NodeSortOption.UPDATED_DESC -> base.sortedByDescending { it.node.updatedAt }
            NodeSortOption.DEGREE_DESC -> base.sortedByDescending { it.totalDegree }
            NodeSortOption.TITLE_ASC -> base.sortedBy { it.node.title }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "节点 (${filteredAndSorted.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "视野：${appUiState.activeBoard?.name ?: "全部主题"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onRequestCreateNode,
                modifier = Modifier.testTag("list_create_node_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("新节点")
            }
        }

        // 主题画板切换与排序芯片行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
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
        }

        // 排序与标签筛选行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NodeSortOption.entries.forEach { option ->
                FilterChip(
                    selected = sortOption == option,
                    onClick = { sortOption = option },
                    label = { Text("按${option.label}") }
                )
            }
            appUiState.allTags.forEach { tag ->
                FilterChip(
                    selected = appUiState.selectedTag == tag,
                    onClick = { knowledgeViewModel.selectTagFilter(tag) },
                    label = { Text("#$tag") }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = filteredAndSorted,
                key = { it.node.id }
            ) { nodeUi ->
                val node = nodeUi.node
                val nodeColor = NodeColorUtil.parseColor(node.color)
                val boardColor = NodeColorUtil.parseColor(nodeUi.boardColorHex)

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (nodeUi.isSelected) 2.dp else 0.dp,
                            color = if (nodeUi.isSelected) nodeColor else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            knowledgeViewModel.selectNode(node.id)
                            onOpenNodeDetail(node.id)
                        }
                        .testTag("node_list_card_${node.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                Text(
                                    text = node.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = boardColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (nodeUi.isCrossBoard) {
                                            "跨界·${nodeUi.boardName}"
                                        } else {
                                            nodeUi.boardName
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = boardColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = DateTimeFormatter.formatRelativeOrShort(node.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = node.summarySnippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (node.tagList.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                node.tagList.forEach { tag ->
                                    Surface(
                                        color = nodeColor.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp)
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "出向 ${nodeUi.outgoingCount} · 入向 ${nodeUi.incomingCount} · (${node.x.toInt()}, ${node.y.toInt()})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        knowledgeViewModel.selectNode(node.id)
                                        onLocateNodeInGraph(node.id)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("定位")
                                }
                                Button(
                                    onClick = {
                                        knowledgeViewModel.selectNode(node.id)
                                        onOpenNodeDetail(node.id)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("打开文档")
                                }
                                IconButton(
                                    onClick = { knowledgeViewModel.deleteNode(node.id) }
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
    }
}
