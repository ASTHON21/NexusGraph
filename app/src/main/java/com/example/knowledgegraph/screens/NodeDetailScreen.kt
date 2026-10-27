package com.example.knowledgegraph.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgegraph.model.EdgeUiState
import com.example.knowledgegraph.model.EditorViewMode
import com.example.knowledgegraph.util.DateTimeFormatter
import com.example.knowledgegraph.util.NodeColorUtil
import com.example.knowledgegraph.viewmodel.KnowledgeViewModel
import com.example.knowledgegraph.viewmodel.NodeDetailViewModel

/**
 * 知识节点详情与 Markdown 编辑器页面
 */
@OptIn(ExperimentalMaterial3Api::class)
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

                NodeMetadataEditorCard(
                    boards = state.boards,
                    boardIdDraft = state.boardIdDraft,
                    titleDraft = state.titleDraft,
                    colorDraft = state.colorDraft,
                    tagListDraft = state.tagListDraft,
                    tagInput = tagInput,
                    onTagInputChange = { tagInput = it },
                    onSelectBoard = { nodeDetailViewModel.updateBoardIdDraft(it) },
                    onTitleChange = { nodeDetailViewModel.updateTitleDraft(it) },
                    onColorChange = { nodeDetailViewModel.updateColorDraft(it) },
                    onAddTag = {
                        if (tagInput.isNotBlank()) {
                            nodeDetailViewModel.addTag(tagInput)
                            tagInput = ""
                        }
                    },
                    onRemoveTag = { nodeDetailViewModel.removeTag(it) }
                )

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
                    MarkdownSyntaxShortcutBar(
                        onInsertOrWrap = { prefix, suffix, placeholder ->
                            insertOrWrapMarkdown(prefix, suffix, placeholder)
                        },
                        onOpenWikiPicker = { showWikiLinkPicker = true }
                    )
                }

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

                NodeSemanticRelationsCard(
                    outgoingEdges = state.outgoingEdges,
                    incomingEdges = state.incomingEdges,
                    backlinkNodes = state.backlinkNodes,
                    boardMap = boardMap,
                    onRequestAddEdge = { showAddEdgeDialog = true },
                    onNavigateToPeerNode = { targetId ->
                        if (state.hasUnsavedChanges) nodeDetailViewModel.saveChanges()
                        onNavigateToNode(targetId)
                    },
                    onEditEdge = { editingEdgeUi = it },
                    onDeleteEdge = { nodeDetailViewModel.deleteRelation(it) }
                )

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
