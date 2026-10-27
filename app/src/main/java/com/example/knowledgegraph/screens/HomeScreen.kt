package com.example.knowledgegraph.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.knowledgegraph.model.AppUiState
import com.example.knowledgegraph.model.BoardOverviewModule
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.util.DateTimeFormatter
import com.example.knowledgegraph.util.NodeColorUtil
import com.example.knowledgegraph.viewmodel.KnowledgeViewModel

/**
 * 知识工作台「总览」页 (HomeScreen)
 * 设计规范：
 * - 纯粹的主题画板模块化总览（支持一键进入「全部主题」全域画板或各独立课题画板）
 * - 顶部呈现全局四维汇总（主题、节点、关系、标签）
 * - 主体采用横竖屏自适应网格（GridCells.Adaptive），每个主题画板作为一个独立标准模块展示其节点、关系、跨界、标签与最近更新概览
 * - 新建主题时自动按序轮换调色板色标，彻底告别多主题同色混淆
 */
@Composable
fun HomeScreen(
    appUiState: AppUiState,
    knowledgeViewModel: KnowledgeViewModel,
    onEnterBoard: (boardId: String?) -> Unit
) {
    var showCreateBoardDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var editingBoard by remember { mutableStateOf<BoardTopic?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 310.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 顶部标题栏（支持明暗主题切换、全量JSON备份、进入全部主题全域画板、新建主题）
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "主题总览",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "按课题划分独立知识空间 · 点击模块进入画板",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier.testTag("backup_workspace_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ImportExport,
                            contentDescription = "JSON备份与恢复"
                        )
                    }
                    IconButton(
                        onClick = { knowledgeViewModel.toggleDarkTheme() },
                        modifier = Modifier.testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (appUiState.isDarkTheme) {
                                Icons.Default.LightMode
                            } else {
                                Icons.Default.DarkMode
                            },
                            contentDescription = "主题明暗切换"
                        )
                    }
                    if (appUiState.boards.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                knowledgeViewModel.selectActiveBoard(null)
                                onEnterBoard(null)
                            },
                            modifier = Modifier.testTag("enter_all_boards_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("全部主题")
                        }
                    }
                    Button(
                        onClick = { showCreateBoardDialog = true },
                        modifier = Modifier.testTag("create_board_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新主题")
                    }
                }
            }
        }

        // 2. 全局四维统计汇总栏（主题 · 节点 · 关系 · 标签，点击可进入全域画板）
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlobalMetricPill(
                    label = "主题",
                    value = "${appUiState.boards.size}",
                    accentHex = "#8B5CF6",
                    onClick = {
                        knowledgeViewModel.selectActiveBoard(null)
                        onEnterBoard(null)
                    },
                    modifier = Modifier.weight(1f)
                )
                GlobalMetricPill(
                    label = "节点",
                    value = "${appUiState.nodes.size}",
                    accentHex = "#6366F1",
                    onClick = {
                        knowledgeViewModel.selectActiveBoard(null)
                        onEnterBoard(null)
                    },
                    modifier = Modifier.weight(1f)
                )
                GlobalMetricPill(
                    label = "关系",
                    value = "${appUiState.edges.size}",
                    accentHex = "#0EA5E9",
                    onClick = {
                        knowledgeViewModel.selectActiveBoard(null)
                        onEnterBoard(null)
                    },
                    modifier = Modifier.weight(1f)
                )
                GlobalMetricPill(
                    label = "标签",
                    value = "${appUiState.allTags.size}",
                    accentHex = "#10B981",
                    onClick = {
                        knowledgeViewModel.selectActiveBoard(null)
                        onEnterBoard(null)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. 空状态或主题画板模块矩阵
        if (appUiState.boardModules.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyBoardsModule(
                    onCreateFirstBoard = { showCreateBoardDialog = true }
                )
            }
        } else {
            items(
                items = appUiState.boardModules,
                key = { it.board.id }
            ) { module ->
                TopicBoardModuleCard(
                    module = module,
                    onClick = {
                        knowledgeViewModel.selectActiveBoard(module.board.id)
                        onEnterBoard(module.board.id)
                    },
                    onEdit = { editingBoard = module.board }
                )
            }
        }
    }

    if (showCreateBoardDialog) {
        TopicBoardDialog(
            initialBoard = null,
            existingBoardCount = appUiState.boards.size,
            canDelete = false,
            onDismiss = { showCreateBoardDialog = false },
            onConfirm = { name, desc, colorHex ->
                showCreateBoardDialog = false
                knowledgeViewModel.createBoard(name, desc, colorHex)
            }
        )
    }

    if (showBackupDialog) {
        WorkspaceBackupDialog(
            initialExportJson = knowledgeViewModel.exportBackupJson(),
            onDismiss = { showBackupDialog = false },
            onImportJson = { jsonText ->
                knowledgeViewModel.importBackupJson(jsonText)
            }
        )
    }

    editingBoard?.let { board ->
        TopicBoardDialog(
            initialBoard = board,
            existingBoardCount = appUiState.boards.size,
            canDelete = true,
            onDismiss = { editingBoard = null },
            onConfirm = { name, desc, colorHex ->
                editingBoard = null
                knowledgeViewModel.saveBoard(
                    board.copy(
                        name = name,
                        description = desc,
                        colorHex = colorHex
                    )
                )
            },
            onDelete = {
                editingBoard = null
                knowledgeViewModel.deleteBoard(board.id)
            }
        )
    }
}

@Composable
private fun GlobalMetricPill(
    label: String,
    value: String,
    accentHex: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = NodeColorUtil.parseColor(accentHex)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单个主题画板的标准概览模块卡片
 * 内部展示该主题的：节点数、内部关系数、跨界关联数、标签数、关系类型分布、高频标签与最新节点状态
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicBoardModuleCard(
    module: BoardOverviewModule,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val board = module.board
    val accent = NodeColorUtil.parseColor(board.colorHex)

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (module.isSelected) 6.dp else 2.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (module.isSelected) 2.dp else 1.dp,
                color = if (module.isSelected) accent else accent.copy(alpha = 0.28f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .testTag("board_module_card_${board.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 模块头部：色标 + 主题名 + 简述 + 编辑按钮
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
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = board.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = board.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑主题",
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 模块核心数据四宫格（节点 · 关系 · 跨界 · 标签）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModuleStatCell(
                    label = "节点",
                    value = "${module.nodeCount}",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                ModuleStatCell(
                    label = "关系",
                    value = "${module.internalEdgeCount}",
                    accent = NodeColorUtil.parseColor("#0EA5E9"),
                    modifier = Modifier.weight(1f)
                )
                ModuleStatCell(
                    label = "跨界",
                    value = "${module.crossBoardEdgeCount}",
                    accent = NodeColorUtil.parseColor("#F59E0B"),
                    modifier = Modifier.weight(1f)
                )
                ModuleStatCell(
                    label = "标签",
                    value = "${module.tagCount}",
                    accent = NodeColorUtil.parseColor("#10B981"),
                    modifier = Modifier.weight(1f)
                )
            }

            // 关系语义与高频标签聚合行
            if (module.relationCounts.isNotEmpty() || module.topTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    module.relationCounts.entries.forEach { (type, count) ->
                        if (count > 0) {
                            val relColor = NodeColorUtil.relationColor(type)
                            Surface(
                                color = relColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${type.symbol}${type.labelCn} $count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = relColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    module.topTags.forEach { tag ->
                        Surface(
                            color = accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 模块底部：最近更新节点与进入画板入口
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (module.latestNodeTitle != null) {
                        "最近：${module.latestNodeTitle} · ${DateTimeFormatter.formatRelativeOrShort(module.latestUpdatedAt)}"
                    } else {
                        "暂无节点 · 点击进入画板创建"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "进入",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "进入画板",
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleStatCell(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyBoardsModule(
    onCreateFirstBoard: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedCard(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Text(
                    text = "暂无主题画板",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "按研究课题或知识领域创建独立主题画板。\n每个主题画板拥有独立的色标、节点与关系，并支持在「全部主题」画板中跨主题连线。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onCreateFirstBoard,
                    modifier = Modifier.testTag("empty_create_first_board_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("创建首个主题画板")
                }
            }
        }
    }
}
