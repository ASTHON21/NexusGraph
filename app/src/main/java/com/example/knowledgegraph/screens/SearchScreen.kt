package com.example.knowledgegraph.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.knowledgegraph.model.AppUiState
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.util.NodeColorUtil
import com.example.knowledgegraph.viewmodel.KnowledgeViewModel

/**
 * 知识图谱全局检索与多维筛选页 (SearchScreen)
 * 支持按主题画板、关键词、标签、关系类型组合过滤并高亮命中文本
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    appUiState: AppUiState,
    knowledgeViewModel: KnowledgeViewModel,
    onOpenNodeDetail: (String) -> Unit,
    onLocateNodeInGraph: (String) -> Unit
) {
    val results = appUiState.canvasNodeUiList
    val hasActiveFilter = appUiState.searchQuery.isNotBlank() ||
        appUiState.selectedTag != null ||
        appUiState.selectedRelationFilter != null ||
        appUiState.activeBoardId != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = appUiState.searchQuery,
            onValueChange = { knowledgeViewModel.updateSearchQuery(it) },
            label = { Text("全文检索") },
            placeholder = { Text("输入标题、正文关键词或 #标签…") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (appUiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { knowledgeViewModel.updateSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "清空")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_query_input")
        )

        // 主题画板范围筛选
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
                FilterChip(
                    selected = appUiState.activeBoardId == board.id,
                    onClick = { knowledgeViewModel.selectActiveBoard(board.id) },
                    label = { Text(board.name) }
                )
            }
        }

        // 标签筛选组
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "标签与关系筛选",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasActiveFilter) {
                    TextButton(
                        onClick = {
                            knowledgeViewModel.clearFilters()
                            knowledgeViewModel.selectActiveBoard(null)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterAltOff,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重置")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RelationType.entries.forEach { type ->
                    FilterChip(
                        selected = appUiState.selectedRelationFilter == type,
                        onClick = { knowledgeViewModel.selectRelationFilter(type) },
                        label = { Text("${type.symbol} ${type.labelCn}") }
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
        }

        Text(
            text = "命中节点：${results.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = results,
                key = { it.node.id }
            ) { nodeUi ->
                val node = nodeUi.node
                val nodeColor = NodeColorUtil.parseColor(node.color)
                val boardColor = NodeColorUtil.parseColor(nodeUi.boardColorHex)
                val highlightColor = MaterialTheme.colorScheme.primary

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            knowledgeViewModel.selectNode(node.id)
                            onOpenNodeDetail(node.id)
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    text = highlightQueryText(
                                        text = node.title,
                                        query = appUiState.searchQuery,
                                        highlightColor = highlightColor
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = boardColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = nodeUi.boardName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = boardColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    knowledgeViewModel.selectNode(node.id)
                                    onLocateNodeInGraph(node.id)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = "定位"
                                )
                            }
                        }

                        Text(
                            text = highlightQueryText(
                                text = node.summarySnippet,
                                query = appUiState.searchQuery,
                                highlightColor = highlightColor
                            ),
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
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
}

private fun highlightQueryText(
    text: String,
    query: String,
    highlightColor: androidx.compose.ui.graphics.Color
) = buildAnnotatedString {
    append(text)
    val q = query.trim()
    if (q.isNotEmpty()) {
        var startIndex = text.indexOf(q, ignoreCase = true)
        while (startIndex >= 0) {
            val endIndex = (startIndex + q.length).coerceAtMost(text.length)
            addStyle(
                style = SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.ExtraBold,
                    background = highlightColor.copy(alpha = 0.18f)
                ),
                start = startIndex,
                end = endIndex
            )
            startIndex = text.indexOf(q, startIndex = endIndex, ignoreCase = true)
        }
    }
}
