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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.util.NodeColorUtil

/**
 * 新建或编辑课题画板空间弹窗：
 * - 新建时按已有主题数量自动轮换默认色标，避免所有主题同色
 * - 打开时自动聚焦名称输入框，支持空标题智能生成「主题 N」
 * - 编辑时含二次删除确认，防止误删
 */
@Composable
fun TopicBoardDialog(
    initialBoard: BoardTopic? = null,
    existingBoardCount: Int = 0,
    canDelete: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, colorHex: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val palette = NodeColorUtil.paletteOptions
    val autoColorHex = palette[existingBoardCount.coerceAtLeast(0) % palette.size].hex
    val defaultNumberedName = "主题 ${existingBoardCount + 1}"

    var name by remember { mutableStateOf(initialBoard?.name ?: "") }
    var description by remember { mutableStateOf(initialBoard?.description ?: "") }
    var selectedColorHex by remember {
        mutableStateOf(initialBoard?.colorHex ?: autoColorHex)
    }
    var confirmDeleteMode by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (initialBoard == null) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    if (confirmDeleteMode && initialBoard != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteMode = false },
            title = {
                Text(
                    text = "确认删除主题画板？",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "删除主题「${initialBoard.name}」将同时清理该主题下的所有节点、关联边及本地 Markdown 文件，此操作不可撤销。"
                )
            },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteMode = false }) {
                    Text("返回")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (initialBoard == null) "新建主题" else "编辑主题",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (initialBoard != null && canDelete && onDelete != null) {
                    IconButton(onClick = { confirmDeleteMode = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "删除主题",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("主题名称") },
                    placeholder = { Text("留空默认命名为「$defaultNumberedName」") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("board_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("课题简述") },
                    placeholder = { Text("简述该画板承载的知识范围") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "主题色标（已自动分配区分色）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NodeColorUtil.paletteOptions.forEach { option ->
                        val isSelected = selectedColorHex.equals(option.hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = option.hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = option.name,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.trim().ifBlank { defaultNumberedName },
                        description.trim().ifBlank { "课题知识空间" },
                        selectedColorHex
                    )
                },
                modifier = Modifier.testTag("confirm_board_dialog_button")
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 新建知识节点弹窗（自动聚焦标题输入框 + 支持按所属主题同步色标与智能序号命名）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateNodeDialog(
    boards: List<BoardTopic>,
    initialBoardId: String?,
    existingNodeCount: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (title: String, markdown: String, tags: List<String>, colorHex: String, boardId: String?) -> Unit
) {
    val defaultBoard = boards.firstOrNull { it.id == initialBoardId }
        ?: boards.firstOrNull()
    var selectedBoardId by remember {
        mutableStateOf(defaultBoard?.id)
    }
    var title by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>() }
    var selectedColorHex by remember {
        mutableStateOf(defaultBoard?.colorHex ?: NodeColorUtil.paletteOptions.first().hex)
    }
    var markdownContent by remember { mutableStateOf("") }
    val defaultNodeTitle = "节点 ${existingNodeCount + 1}"
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "新建节点",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (boards.isNotEmpty()) {
                    Text(
                        text = "所属主题",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        boards.forEach { board ->
                            val bColor = NodeColorUtil.parseColor(board.colorHex)
                            FilterChip(
                                selected = selectedBoardId == board.id,
                                onClick = {
                                    selectedBoardId = board.id
                                    selectedColorHex = board.colorHex
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(bColor)
                                    )
                                },
                                label = { Text(board.name) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("节点标题") },
                    placeholder = { Text("输入标题，留空默认为「$defaultNodeTitle」") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("create_node_title_input")
                )

                Text(
                    text = "节点色标",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NodeColorUtil.paletteOptions.forEach { option ->
                        val isSelected = selectedColorHex.equals(option.hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = option.hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = option.name,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
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
                        onValueChange = { tagInput = it },
                        label = { Text("标签") },
                        placeholder = { Text("输入标签后点击 +") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val clean = tagInput.trim().removePrefix("#").trim()
                            if (clean.isNotEmpty() && !tags.contains(clean)) {
                                tags.add(clean)
                            }
                            tagInput = ""
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "添加标签")
                    }
                }

                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tags.remove(tag) },
                                label = { Text("#$tag") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "移除",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = markdownContent,
                    onValueChange = { markdownContent = it },
                    label = { Text("Markdown 正文（可选，支持 [[双链]]）") },
                    placeholder = { Text("直接撰写内容，或创建后在详情页分屏编辑…") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_node_markdown_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTags = tags.toMutableList()
                    val pendingTag = tagInput.trim().removePrefix("#").trim()
                    if (pendingTag.isNotEmpty() && !finalTags.contains(pendingTag)) {
                        finalTags.add(pendingTag)
                    }
                    val cleanTitle = title.trim().ifBlank { defaultNodeTitle }
                    val finalMarkdown = markdownContent.ifBlank { "# $cleanTitle\n\n" }
                    onConfirm(
                        cleanTitle,
                        finalMarkdown,
                        finalTags,
                        selectedColorHex,
                        selectedBoardId
                    )
                },
                modifier = Modifier.testTag("confirm_create_node_button")
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 新建节点间语义关系弹窗（支持同主题及跨主题节点关联）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateEdgeDialog(
    nodes: List<NodeData>,
    boards: List<BoardTopic> = emptyList(),
    initialFromNodeId: String? = null,
    initialToNodeId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (fromNodeId: String, toNodeId: String, type: RelationType, label: String) -> Unit
) {
    if (nodes.size < 2) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("节点不足") },
            text = { Text("至少需要 2 个节点才能创建语义连接。") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("确定") }
            }
        )
        return
    }

    val boardMap = remember(boards) { boards.associateBy { it.id } }
    fun formatNodeWithBoard(node: NodeData): String {
        val bName = boardMap[node.boardId]?.name
        return if (bName != null) "⟦$bName⟧ ${node.title}" else node.title
    }

    var fromNode by remember {
        mutableStateOf(
            nodes.firstOrNull { it.id == initialFromNodeId } ?: nodes.first()
        )
    }
    var toNode by remember {
        mutableStateOf(
            nodes.firstOrNull { it.id == initialToNodeId && it.id != fromNode.id }
                ?: nodes.first { it.id != fromNode.id }
        )
    }
    var selectedType by remember { mutableStateOf(RelationType.RELATED) }
    var customLabel by remember { mutableStateOf("") }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }

    val isCrossBoard = fromNode.boardId != toNode.boardId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "新建关系",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = fromExpanded,
                    onExpandedChange = { fromExpanded = it }
                ) {
                    OutlinedTextField(
                        value = formatNodeWithBoard(fromNode),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("起点节点") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = fromExpanded,
                        onDismissRequest = { fromExpanded = false }
                    ) {
                        nodes.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(formatNodeWithBoard(candidate)) },
                                onClick = {
                                    fromNode = candidate
                                    if (toNode.id == candidate.id) {
                                        nodes.firstOrNull { it.id != candidate.id }?.let {
                                            toNode = it
                                        }
                                    }
                                    fromExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "关系类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RelationType.entries.forEach { type ->
                        val selected = selectedType == type
                        FilterChip(
                            selected = selected,
                            onClick = { selectedType = type },
                            label = {
                                Text("${type.symbol} ${type.labelCn}")
                            }
                        )
                    }
                }

                Surface(
                    color = NodeColorUtil.relationColor(selectedType).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(NodeColorUtil.relationColor(selectedType))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedType.labelCn}（${selectedType.name}）：${selectedType.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = toExpanded,
                    onExpandedChange = { toExpanded = it }
                ) {
                    OutlinedTextField(
                        value = formatNodeWithBoard(toNode),
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(if (isCrossBoard) "目标节点（跨主题关联）" else "目标节点")
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = toExpanded,
                        onDismissRequest = { toExpanded = false }
                    ) {
                        nodes.filter { it.id != fromNode.id }.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(formatNodeWithBoard(candidate)) },
                                onClick = {
                                    toNode = candidate
                                    toExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it },
                    label = { Text("边签说明（可选）") },
                    placeholder = { Text("默认显示「${selectedType.labelCn}」") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fromNode.id != toNode.id) {
                        onConfirm(
                            fromNode.id,
                            toNode.id,
                            selectedType,
                            customLabel.ifBlank { selectedType.labelCn }
                        )
                    }
                },
                modifier = Modifier.testTag("confirm_create_edge_button")
            ) {
                Text("连接")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 编辑既有语义关系弹窗（支持修改 RelationType 与边签文字）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditEdgeDialog(
    edge: EdgeData,
    fromNodeTitle: String,
    toNodeTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (newType: RelationType, newLabel: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(edge.type) }
    var labelText by remember { mutableStateOf(edge.label) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "编辑关系",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "$fromNodeTitle  ──►  $toNodeTitle",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RelationType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                if (labelText == selectedType.labelCn || labelText.isBlank()) {
                                    labelText = type.labelCn
                                }
                                selectedType = type
                            },
                            label = { Text("${type.symbol} ${type.labelCn}") }
                        )
                    }
                }
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("边签说明") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedType, labelText.ifBlank { selectedType.labelCn })
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 双链节点插入选择器弹窗（供 Markdown 编辑器在光标处一键插入 [[目标节点名]]）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WikiLinkPickerDialog(
    candidateNodes: List<NodeData>,
    boards: List<BoardTopic>,
    onDismiss: () -> Unit,
    onSelectTitle: (String) -> Unit
) {
    var customTitle by remember { mutableStateOf("") }
    val boardMap = remember(boards) { boards.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "插入双链引用 [[ ]]",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("输入目标节点标题") },
                    placeholder = { Text("可直接输入新概念名或从下方点选已有节点") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (candidateNodes.isNotEmpty()) {
                    Text(
                        text = "点选已有节点",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        candidateNodes.forEach { node ->
                            val bName = boardMap[node.boardId]?.name
                            FilterChip(
                                selected = customTitle.equals(node.title, ignoreCase = true),
                                onClick = { onSelectTitle(node.title) },
                                label = {
                                    Text(
                                        if (bName != null) "⟦$bName⟧ ${node.title}" else node.title
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = customTitle.trim()
                    if (target.isNotEmpty()) {
                        onSelectTitle(target)
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("插入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 全量工作区 JSON 备份导出与恢复导入弹窗
 */
@Composable
fun WorkspaceBackupDialog(
    initialExportJson: String,
    onDismiss: () -> Unit,
    onImportJson: (String) -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var jsonContent by remember { mutableStateOf(initialExportJson) }
    var copiedBanner by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "工作区 JSON 备份与恢复",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "可一键复制下方完整 JSON 备份以跨设备保存，或粘贴已有的备份 JSON 点击「导入恢复」合并到当前工作区。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(jsonContent))
                            copiedBanner = true
                        },
                        modifier = Modifier.testTag("copy_backup_json_button")
                    ) {
                        Text(if (copiedBanner) "已复制备份 JSON" else "复制备份 JSON")
                    }
                    TextButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { clip ->
                                if (clip.isNotBlank()) {
                                    jsonContent = clip
                                }
                            }
                        }
                    ) {
                        Text("从剪贴板粘贴")
                    }
                }

                OutlinedTextField(
                    value = jsonContent,
                    onValueChange = {
                        jsonContent = it
                        copiedBanner = false
                    },
                    label = { Text("JSON 备份数据（含全部主题、节点坐标、Markdown 与关系边）") },
                    minLines = 8,
                    maxLines = 14,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_json_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onImportJson(jsonContent)
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_import_backup_button")
            ) {
                Text("导入恢复")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

