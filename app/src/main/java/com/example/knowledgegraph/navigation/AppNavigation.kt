package com.example.knowledgegraph.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.knowledgegraph.di.AppContainer
import com.example.knowledgegraph.screens.CreateEdgeDialog
import com.example.knowledgegraph.screens.CreateNodeDialog
import com.example.knowledgegraph.screens.GraphBoardScreen
import com.example.knowledgegraph.screens.HomeScreen
import com.example.knowledgegraph.screens.NodeDetailScreen
import com.example.knowledgegraph.screens.NodeListScreen
import com.example.knowledgegraph.screens.SearchScreen
import com.example.knowledgegraph.ui.theme.KnowledgeGraphTheme
import com.example.knowledgegraph.viewmodel.GraphViewModel
import com.example.knowledgegraph.viewmodel.KnowledgeViewModel
import com.example.knowledgegraph.viewmodel.NodeDetailViewModel

object NavRoutes {
    const val HOME = "workspace_home"
    const val GRAPH_BOARD = "graph_board"
    const val NODE_LIST = "node_list"
    const val SEARCH = "search_filter"
    const val NODE_DETAIL = "node_detail/{nodeId}"

    fun nodeDetailRoute(nodeId: String): String = "node_detail/$nodeId"
}

data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

// 标准化 2 字主导航标签
private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = NavRoutes.HOME,
        label = "总览",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_tab_home"
    ),
    TopLevelDestination(
        route = NavRoutes.GRAPH_BOARD,
        label = "画板",
        selectedIcon = Icons.Filled.Hub,
        unselectedIcon = Icons.Outlined.Hub,
        testTag = "nav_tab_graph"
    ),
    TopLevelDestination(
        route = NavRoutes.NODE_LIST,
        label = "节点",
        selectedIcon = Icons.Filled.FormatListBulleted,
        unselectedIcon = Icons.Outlined.FormatListBulleted,
        testTag = "nav_tab_nodes"
    ),
    TopLevelDestination(
        route = NavRoutes.SEARCH,
        label = "检索",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "nav_tab_search"
    )
)

/**
 * 响应式主应用骨架：支持竖屏底部导航栏 (NavigationBar) 与横屏/宽屏侧边导航栏 (NavigationRail)
 */
@Composable
fun KnowledgeGraphApp(container: AppContainer) {
    val knowledgeViewModel: KnowledgeViewModel = viewModel(factory = container.viewModelFactory)
    val graphViewModel: GraphViewModel = viewModel(factory = container.viewModelFactory)
    val appUiState by knowledgeViewModel.uiState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.HOME
    val isDetailRoute = currentRoute.startsWith("node_detail")

    var showCreateNodeDialog by remember { mutableStateOf(false) }
    var showCreateEdgeDialog by remember { mutableStateOf(false) }
    var initialEdgeFromNodeId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(appUiState.statusMessage) {
        appUiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            knowledgeViewModel.clearStatusMessage()
        }
    }

    KnowledgeGraphTheme(darkTheme = appUiState.isDarkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail && !isDetailRoute) {
                        NavigationBar {
                            topLevelDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail && !isDetailRoute) {
                        NavigationRail {
                            topLevelDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = NavRoutes.HOME,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(NavRoutes.HOME) {
                                HomeScreen(
                                    appUiState = appUiState,
                                    knowledgeViewModel = knowledgeViewModel,
                                    onEnterBoard = { boardId ->
                                        knowledgeViewModel.selectActiveBoard(boardId)
                                        navController.navigate(NavRoutes.GRAPH_BOARD) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }

                            composable(NavRoutes.GRAPH_BOARD) {
                                GraphBoardScreen(
                                    appUiState = appUiState,
                                    knowledgeViewModel = knowledgeViewModel,
                                    graphViewModel = graphViewModel,
                                    onOpenNodeDetail = { nodeId ->
                                        navController.navigate(NavRoutes.nodeDetailRoute(nodeId))
                                    },
                                    onRequestCreateNode = { showCreateNodeDialog = true },
                                    onRequestCreateEdge = { fromNodeId ->
                                        initialEdgeFromNodeId = fromNodeId
                                        showCreateEdgeDialog = true
                                    }
                                )
                            }

                            composable(NavRoutes.NODE_LIST) {
                                NodeListScreen(
                                    appUiState = appUiState,
                                    knowledgeViewModel = knowledgeViewModel,
                                    onOpenNodeDetail = { nodeId ->
                                        navController.navigate(NavRoutes.nodeDetailRoute(nodeId))
                                    },
                                    onLocateNodeInGraph = { nodeId ->
                                        appUiState.nodes.firstOrNull { it.id == nodeId }?.let { node ->
                                            graphViewModel.centerOnNode(node)
                                        }
                                        navController.navigate(NavRoutes.GRAPH_BOARD) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onRequestCreateNode = { showCreateNodeDialog = true }
                                )
                            }

                            composable(NavRoutes.SEARCH) {
                                SearchScreen(
                                    appUiState = appUiState,
                                    knowledgeViewModel = knowledgeViewModel,
                                    onOpenNodeDetail = { nodeId ->
                                        navController.navigate(NavRoutes.nodeDetailRoute(nodeId))
                                    },
                                    onLocateNodeInGraph = { nodeId ->
                                        appUiState.nodes.firstOrNull { it.id == nodeId }?.let { node ->
                                            graphViewModel.centerOnNode(node)
                                        }
                                        navController.navigate(NavRoutes.GRAPH_BOARD) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }

                            composable(
                                route = NavRoutes.NODE_DETAIL,
                                arguments = listOf(
                                    navArgument("nodeId") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val nodeId = backStackEntry.arguments?.getString("nodeId") ?: return@composable
                                val detailViewModel: NodeDetailViewModel = viewModel(
                                    key = "detail_$nodeId",
                                    factory = container.viewModelFactory
                                )
                                NodeDetailScreen(
                                    nodeId = nodeId,
                                    nodeDetailViewModel = detailViewModel,
                                    knowledgeViewModel = knowledgeViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToNode = { nextNodeId ->
                                        if (nextNodeId != nodeId) {
                                            navController.navigate(NavRoutes.nodeDetailRoute(nextNodeId))
                                        }
                                    },
                                    onLocateInGraph = { targetNodeId ->
                                        appUiState.nodes.firstOrNull { it.id == targetNodeId }?.let { node ->
                                            graphViewModel.centerOnNode(node)
                                        }
                                        navController.popBackStack()
                                        navController.navigate(NavRoutes.GRAPH_BOARD) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showCreateNodeDialog) {
                CreateNodeDialog(
                    boards = appUiState.boards,
                    initialBoardId = appUiState.activeBoardId,
                    existingNodeCount = appUiState.nodes.size,
                    onDismiss = { showCreateNodeDialog = false },
                    onConfirm = { title, markdown, tags, colorHex, boardId ->
                        showCreateNodeDialog = false
                        val spawnCenter = graphViewModel.currentViewportCenterWorld()
                        knowledgeViewModel.createNewNode(
                            title = title,
                            markdownContent = markdown,
                            tagList = tags,
                            color = colorHex,
                            boardId = boardId,
                            spawnCenterWorld = spawnCenter,
                            onCreated = { created ->
                                graphViewModel.selectNode(created.id)
                            }
                        )
                    }
                )
            }

            if (showCreateEdgeDialog) {
                CreateEdgeDialog(
                    nodes = appUiState.nodes,
                    boards = appUiState.boards,
                    initialFromNodeId = initialEdgeFromNodeId,
                    onDismiss = { showCreateEdgeDialog = false },
                    onConfirm = { fromId, toId, type, label ->
                        showCreateEdgeDialog = false
                        knowledgeViewModel.createEdge(
                            fromNodeId = fromId,
                            toNodeId = toId,
                            type = type,
                            label = label
                        )
                    }
                )
            }
        }
    }
}
