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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeUiState
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.util.MarkdownPreview
import com.example.knowledgegraph.util.NodeColorUtil

/**
 * 节点元信息编辑卡片：所属主题画板切换、节点标题、色标选择、标签管理
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeMetadataEditorCard(
    boards: List<BoardTopic>,
    boardIdDraft: String,
    titleDraft: String,
    colorDraft: String,
    tagListDraft: List<String>,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    onSelectBoard: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (boards.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "所属主题：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    boards.forEach { board ->
                        val boardColor = NodeColorUtil.parseColor(board.colorHex)
                        FilterChip(
                            selected = boardIdDraft == board.id,
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
                }
            }

            OutlinedTextField(
                value = titleDraft,
                onValueChange = onTitleChange,
                label = { Text("节点标题") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("node_title_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "节点色标：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NodeColorUtil.paletteOptions.forEach { option ->
                    val isSelected = colorDraft.equals(option.hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(option.color)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    Color.Transparent
                                },
                                shape = CircleShape
                            )
                            .clickable { onColorChange(option.hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = option.name,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = onTagInputChange,
                    label = { Text("标签") },
                    placeholder = { Text("输入标签名后点击添加") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_tag_input")
                )
                FilledTonalButton(
                    onClick = onAddTag,
                    modifier = Modifier.testTag("add_tag_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加")
                }
            }

            if (tagListDraft.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tagListDraft.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveTag(tag) },
                            label = { Text("#$tag") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Markdown 光标感知快捷语法插入栏
 */
@Composable
fun MarkdownSyntaxShortcutBar(
    onInsertOrWrap: (prefix: String, suffix: String, placeholder: String) -> Unit,
    onOpenWikiPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AssistChip(
            onClick = { onInsertOrWrap("# ", "", "一级标题") },
            label = { Text("H1") }
        )
        AssistChip(
            onClick = { onInsertOrWrap("## ", "", "二级标题") },
            label = { Text("H2") }
        )
        AssistChip(
            onClick = { onInsertOrWrap("**", "**", "重点") },
            label = { Text("粗体") }
        )
        AssistChip(
            onClick = { onInsertOrWrap("*", "*", "强调") },
            label = { Text("斜体") }
        )
        AssistChip(
            onClick = onOpenWikiPicker,
            label = { Text("双链") }
        )
        AssistChip(
            onClick = { onInsertOrWrap("> ", "", "摘录") },
            label = { Text("引用") }
        )
        AssistChip(
            onClick = { onInsertOrWrap("- ", "", "列表项") },
            label = { Text("列表") }
        )
        AssistChip(
            onClick = { onInsertOrWrap("- [ ] ", "", "待办事项") },
            label = { Text("待办") }
        )
        AssistChip(
            onClick = { onInsertOrWrap("```kotlin\n", "\n```", "// 代码") },
            label = { Text("代码") }
        )
        AssistChip(
            onClick = { onInsertOrWrap("\n---\n", "", "") },
            label = { Text("分割") }
        )
    }
}

@Composable
fun MarkdownSourceField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    minLines: Int,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Markdown 源码（支持 #标题、**粗体**、```代码、[[双链]]）") },
        minLines = minLines,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 21.sp
        ),
        modifier = modifier.testTag("node_markdown_editor")
    )
}

@Composable
fun MarkdownPreviewCard(
    markdown: String,
    accentColor: Color,
    onWikiLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("node_markdown_preview_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "渲染预览（点击 ⟦双链⟧ 跳转或创建节点）",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            MarkdownPreview(
                markdown = markdown,
                onWikiLinkClick = onWikiLinkClick
            )
        }
    }
}

/**
 * 节点语义关系与反向双链面板
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeSemanticRelationsCard(
    outgoingEdges: List<EdgeUiState>,
    incomingEdges: List<EdgeUiState>,
    backlinkNodes: List<NodeData>,
    boardMap: Map<String, BoardTopic>,
    onRequestAddEdge: () -> Unit,
    onNavigateToPeerNode: (String) -> Unit,
    onEditEdge: (EdgeUiState) -> Unit,
    onDeleteEdge: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "语义关系",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "出向 ${outgoingEdges.size} · 入向 ${incomingEdges.size} · 反向双链 ${backlinkNodes.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onRequestAddEdge,
                    modifier = Modifier.testTag("detail_add_relation_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLink,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新建关系")
                }
            }

            if (outgoingEdges.isEmpty() && incomingEdges.isEmpty() && backlinkNodes.isEmpty()) {
                Text(
                    text = "暂无关联，点击「新建关系」或在正文插入 [[节点名]] 自动建立引用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (outgoingEdges.isNotEmpty()) {
                Text(
                    text = "出向",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                outgoingEdges.forEach { edgeUi ->
                    RelationEdgeRow(
                        edgeUi = edgeUi,
                        boardMap = boardMap,
                        isOutgoing = true,
                        onNodeClick = onNavigateToPeerNode,
                        onEditEdge = { onEditEdge(edgeUi) },
                        onDeleteEdge = { onDeleteEdge(edgeUi.edge.id) }
                    )
                }
            }

            if (incomingEdges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "入向",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                incomingEdges.forEach { edgeUi ->
                    RelationEdgeRow(
                        edgeUi = edgeUi,
                        boardMap = boardMap,
                        isOutgoing = false,
                        onNodeClick = onNavigateToPeerNode,
                        onEditEdge = { onEditEdge(edgeUi) },
                        onDeleteEdge = { onDeleteEdge(edgeUi.edge.id) }
                    )
                }
            }

            if (backlinkNodes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "反向双链",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    backlinkNodes.forEach { backNode ->
                        AssistChip(
                            onClick = { onNavigateToPeerNode(backNode.id) },
                            label = { Text("⟦${backNode.title}⟧") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationEdgeRow(
    edgeUi: EdgeUiState,
    boardMap: Map<String, BoardTopic>,
    isOutgoing: Boolean,
    onNodeClick: (String) -> Unit,
    onEditEdge: () -> Unit,
    onDeleteEdge: () -> Unit
) {
    val relColor = NodeColorUtil.relationColor(edgeUi.edge.type)
    val peerNode = if (isOutgoing) edgeUi.toNode else edgeUi.fromNode
    val peerBoardName = peerNode?.let { boardMap[it.boardId]?.name }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { peerNode?.let { onNodeClick(it.id) } }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = relColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${edgeUi.edge.type.symbol} ${edgeUi.edge.displayLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = relColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (edgeUi.isCrossBoard && peerBoardName != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "跨界·$peerBoardName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = if (isOutgoing) {
                        "→ ${peerNode?.title ?: "未知"}"
                    } else {
                        "← ${peerNode?.title ?: "未知"}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row {
                IconButton(
                    onClick = onEditEdge,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑",
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteEdge,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
