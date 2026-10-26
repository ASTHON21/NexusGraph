package com.example.knowledgegraph.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeUiState
import com.example.knowledgegraph.model.EditorViewMode
import com.example.knowledgegraph.util.DateTimeFormatter
import com.example.knowledgegraph.util.MarkdownPreview
import com.example.knowledgegraph.util.NodeColorUtil
import com.example.knowledgegraph.viewmodel.KnowledgeViewModel
import com.example.knowledgegraph.viewmodel.NodeDetailViewModel

/**
 * 知识节点详情与 Markdown 编辑器页面
 * 支持：
 * - 切换所属主题画板 (BoardTopic)
 * - 标准化 2 字模式切换（源码 / 分屏 / 预览）
 * - 光标感知语法插入（TextFieldValue）与真实节点双链选择器（WikiLinkPickerDialog）
 * - 横屏分屏模式下自动采用左右并排双栏对照（左源码、右预览）
 * - 支持同主题与跨主题语义关系管理、已有关系修改及反向双链跳转
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NodeDetailScreen(
    nodeId: String,
    nodeDetailViewModel: NodeDetailViewModel,
    knowledgeViewModel: KnowledgeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNode: (String) -> Unit,
    onLocateInGraph: (String) -> Unit
) {
    LaunchedEffect(nodeId) {
        nodeDetailViewModel.bindNode(nodeId)
    }

    val state by nodeDetailViewModel.uiState.collectAsStateWithLifecycle()
    val boardMap = remember(state.boards) { state.boards.associateBy { it.id } }
    var tagInput by remember { mutableStateOf("") }
    var showAddEdgeDialog by remember { mutableStateOf(false) }
    var showWikiLinkPicker by remember { mutableStateOf(false) }
    var editingEdgeUi by remember { mutableStateOf<EdgeUiState?>(null) }

    // 维护带光标/选区状态的 TextFieldValue，并与 ViewModel 的 markdownDraft 保持双向同步
    var markdownFieldValue by remember(nodeId) {
        mutableStateOf(
            TextFieldValue(
                text = state.markdownDraft,
                selection = TextRange(state.markdownDraft.length)
            )
        )
    }
    LaunchedEffect(state.markdownDraft) {
        if (markdownFieldValue.text != state.markdownDraft) {
            val safeCursor = markdownFieldValue.selection.end.coerceIn(0, state.markdownDraft.length)
            markdownFieldValue = TextFieldValue(
                text = state.markdownDraft,
                selection = TextRange(safeCursor)
            )
        }
    }

    // 在当前光标位置插入或包裹选中文本
    fun insertOrWrapMarkdown(prefix: String, suffix: String = "", defaultPlaceholder: String = "") {
        val currentText = markdownFieldValue.text
        val start = markdownFieldValue.selection.min.coerceIn(0, currentText.length)
        val end = markdownFieldValue.selection.max.coerceIn(0, currentText.length)
        val selected = currentText.substring(start, end)
        val inner = selected.ifEmpty { defaultPlaceholder }
        val replacement = "$prefix$inner$suffix"
        val newText = currentText.replaceRange(start, end, replacement)
        val newCursorStart = start + prefix.length
        val newCursorEnd = newCursorStart + inner.length
        markdownFieldValue = TextFieldValue(
            text = newText,
            selection = if (selected.isEmpty() && defaultPlaceholder.isNotEmpty()) {
                TextRange(newCursorStart, newCursorEnd)
            } else {
                TextRange(start + replacement.length)
            }
        )
        nodeDetailViewModel.updateMarkdownDraft(newText)
    }

    BackHandler {
        if (state.hasUnsavedChanges) {
            nodeDetailViewModel.saveChanges()
        }
        onNavigateBack()
    }

    val accentColor = NodeColorUtil.parseColor(state.colorDraft)
    val currentBoardName = boardMap[state.boardIdDraft]?.name ?: "未归类"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.titleDraft.ifBlank { "编辑节点" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        state.originalNode?.let { node ->
                            Text(
                                text = "⟦$currentBoardName⟧ · ${DateTimeFormatter.formatRelativeOrShort(node.updatedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.hasUnsavedChanges) {
                                nodeDetailViewModel.saveChanges()
                            }
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (state.hasUnsavedChanges) {
                                nodeDetailViewModel.saveChanges()
                            }
                            onLocateInGraph(nodeId)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "画板定位"
                        )
                    }
                    IconButton(
                        onClick = {
                            knowledgeViewModel.deleteNode(nodeId)
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("delete_node_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { nodeDetailViewModel.saveChanges() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_node_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (state.hasUnsavedChanges) "保存*" else "已存")
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 640.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(visible = state.saveBannerMessage != null) {
                    state.saveBannerMessage?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                IconButton(
                                    onClick = { nodeDetailViewModel.clearBannerMessage() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. 节点元信息卡片（所属主题画板、标题、色标、标签）
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 所属主题画板切换行
                        if (state.boards.isNotEmpty()) {
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
                                state.boards.forEach { board ->
                                    val boardColor = NodeColorUtil.parseColor(board.colorHex)
                                    FilterChip(
                                        selected = state.boardIdDraft == board.id,
                                        onClick = { nodeDetailViewModel.updateBoardIdDraft(board.id) },
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
                            value = state.titleDraft,
                            onValueChange = { nodeDetailViewModel.updateTitleDraft(it) },
                            label = { Text("节点标题") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("node_title_input")
                        )

                        // 节点颜色选择器
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
                                val isSelected = state.colorDraft.equals(option.hex, ignoreCase = true)
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
                                        .clickable { nodeDetailViewModel.updateColorDraft(option.hex) },
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

                        // 标签编辑区
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = tagInput,
                                onValueChange = { tagInput = it },
                                label = { Text("标签") },
                                placeholder = { Text("输入标签名后点击添加") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_tag_input")
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (tagInput.isNotBlank()) {
                                        nodeDetailViewModel.addTag(tagInput)
                                        tagInput = ""
                                    }
                                },
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

                        if (state.tagListDraft.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                state.tagListDraft.forEach { tag ->
                                    InputChip(
                                        selected = true,
                                        onClick = { nodeDetailViewModel.removeTag(tag) },
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

                // 2. 源码 / 分屏 / 预览 切换栏与光标感知快捷语法栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "文档内容",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EditorViewMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.editorViewMode == mode,
                                onClick = { nodeDetailViewModel.setEditorViewMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("editor_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }

                if (state.editorViewMode != EditorViewMode.PREVIEW) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { insertOrWrapMarkdown("# ", "", "一级标题") },
                            label = { Text("H1") }
                        )
                        AssistChip(
                            onClick = { insertOrWrapMarkdown("## ", "", "二级标题") },
                            label = { Text("H2") }
                        )
                        AssistChip(
                            onClick = { insertOrWrapMarkdown("**", "**", "重点") },
                            label = { Text("粗体") }
                        )
                        AssistChip(
                            onClick = { insertOrWrapMarkdown("*", "*", "强调") },
                            label = { Text("斜体") }
                        )
                        AssistChip(
                            onClick = { showWikiLinkPicker = true },
                            label = { Text("双链") }
                        )
                        AssistChip(
                            onClick = { insertOrWrapMarkdown("> ", "", "摘录") },
                            label = { Text("引用") }
                        )
                        AssistChip(
                            onClick = { insertOrWrapMarkdown("- ", "", "列表项") },
                            label = { Text("列表") }
                        )
                        AssistChip(
                            onClick = { insertOrWrapMarkdown("- [ ] ", "", "待办事项") },
                            label = { Text("待办") }
                        )
                        AssistChip(
                            onClick = { insertOrWrapMarkdown("```kotlin\n", "\n```", "// 代码") },
                            label = { Text("代码") }
                        )
                        AssistChip(
                            onClick = { insertOrWrapMarkdown("\n---\n") },
                            label = { Text("分割") }
                        )
                    }
                }

                // 3. 自适应源码与预览区域（横屏分屏模式下左右双栏并排，竖屏下上下排布）
                if (state.editorViewMode == EditorViewMode.SPLIT && isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        MarkdownSourceField(
                            value = markdownFieldValue,
                            onValueChange = { newValue ->
                                markdownFieldValue = newValue
                                nodeDetailViewModel.updateMarkdownDraft(newValue.text)
                            },
                            minLines = 12,
                            modifier = Modifier.weight(1f)
                        )
                        MarkdownPreviewCard(
                            markdown = state.markdownDraft,
                            accentColor = accentColor,
                            onWikiLinkClick = { wikiTitle ->
                                if (state.hasUnsavedChanges) nodeDetailViewModel.saveChanges()
                                knowledgeViewModel.openOrCreateNodeByWikiTitle(
                                    sourceNodeId = nodeId,
                                    wikiTitle = wikiTitle,
                                    onNavigateToNode = onNavigateToNode
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    if (state.editorViewMode == EditorViewMode.EDIT || state.editorViewMode == EditorViewMode.SPLIT) {
                        MarkdownSourceField(
                            value = markdownFieldValue,
                            onValueChange = { newValue ->
                                markdownFieldValue = newValue
                                nodeDetailViewModel.updateMarkdownDraft(newValue.text)
                            },
                            minLines = if (state.editorViewMode == EditorViewMode.EDIT) 12 else 7,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (state.editorViewMode == EditorViewMode.PREVIEW || state.editorViewMode == EditorViewMode.SPLIT) {
                        MarkdownPreviewCard(
                            markdown = state.markdownDraft,
                            accentColor = accentColor,
                            onWikiLinkClick = { wikiTitle ->
                                if (state.hasUnsavedChanges) nodeDetailViewModel.saveChanges()
                                knowledgeViewModel.openOrCreateNodeByWikiTitle(
                                    sourceNodeId = nodeId,
                                    wikiTitle = wikiTitle,
                                    onNavigateToNode = onNavigateToNode
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 4. 节点语义关系网络（支持同主题与跨主题关联、编辑关系及反向双链）
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
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
                                    text = "出向 ${state.outgoingEdges.size} · 入向 ${state.incomingEdges.size} · 反向双链 ${state.backlinkNodes.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { showAddEdgeDialog = true },
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

                        if (state.outgoingEdges.isEmpty() && state.incomingEdges.isEmpty() && state.backlinkNodes.isEmpty()) {
                            Text(
                                text = "暂无关联，点击「新建关系」或在正文插入 [[节点名]] 自动建立引用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state.outgoingEdges.isNotEmpty()) {
                            Text(
                                text = "出向",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            state.outgoingEdges.forEach { edgeUi ->
                                RelationEdgeRow(
                                    edgeUi = edgeUi,
                                    boardMap = boardMap,
                                    isOutgoing = true,
                                    onNodeClick = { targetId ->
                                        if (state.hasUnsavedChanges) nodeDetailViewModel.saveChanges()
                                        onNavigateToNode(targetId)
                                    },
                                    onEditEdge = { editingEdgeUi = edgeUi },
                                    onDeleteEdge = { nodeDetailViewModel.deleteRelation(edgeUi.edge.id) }
                                )
                            }
                        }

                        if (state.incomingEdges.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "入向",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            state.incomingEdges.forEach { edgeUi ->
                                RelationEdgeRow(
                                    edgeUi = edgeUi,
                                    boardMap = boardMap,
                                    isOutgoing = false,
                                    onNodeClick = { sourceId ->
                                        if (state.hasUnsavedChanges) nodeDetailViewModel.saveChanges()
                                        onNavigateToNode(sourceId)
                                    },
                                    onEditEdge = { editingEdgeUi = edgeUi },
                                    onDeleteEdge = { nodeDetailViewModel.deleteRelation(edgeUi.edge.id) }
                                )
                            }
                        }

                        if (state.backlinkNodes.isNotEmpty()) {
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
                                state.backlinkNodes.forEach { backNode ->
                                    AssistChip(
                                        onClick = {
                                            if (state.hasUnsavedChanges) nodeDetailViewModel.saveChanges()
                                            onNavigateToNode(backNode.id)
                                        },
                                        label = { Text("⟦${backNode.title}⟧") }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showAddEdgeDialog) {
        val allNodes = buildList {
            state.originalNode?.let { add(it) }
            addAll(state.availableTargetNodes)
        }
        CreateEdgeDialog(
            nodes = allNodes,
            boards = state.boards,
            initialFromNodeId = nodeId,
            onDismiss = { showAddEdgeDialog = false },
            onConfirm = { fromId, toId, relType, label ->
                if (fromId == nodeId) {
                    nodeDetailViewModel.addRelationFromCurrentNode(toId, relType, label)
                } else {
                    knowledgeViewModel.createEdge(fromId, toId, relType, label)
                }
                showAddEdgeDialog = false
            }
        )
    }

    if (showWikiLinkPicker) {
        WikiLinkPickerDialog(
            candidateNodes = state.availableTargetNodes,
            boards = state.boards,
            onDismiss = { showWikiLinkPicker = false },
            onSelectTitle = { targetTitle ->
                showWikiLinkPicker = false
                insertOrWrapMarkdown("[[$targetTitle]]")
            }
        )
    }

    editingEdgeUi?.let { targetEdgeUi ->
        EditEdgeDialog(
            edge = targetEdgeUi.edge,
            fromNodeTitle = targetEdgeUi.fromNode?.title ?: "起点",
            toNodeTitle = targetEdgeUi.toNode?.title ?: "终点",
            onDismiss = { editingEdgeUi = null },
            onConfirm = { newType, newLabel ->
                nodeDetailViewModel.updateRelation(targetEdgeUi.edge, newType, newLabel)
                editingEdgeUi = null
            }
        )
    }
}

@Composable
private fun MarkdownSourceField(
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
private fun MarkdownPreviewCard(
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
