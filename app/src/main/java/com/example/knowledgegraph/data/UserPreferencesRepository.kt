package com.example.knowledgegraph.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "knowledge_workspace_prefs"
)

const val ALL_BOARDS_SENTINEL = "__ALL_BOARDS__"

data class WorkspacePreferences(
    val isDarkTheme: Boolean = true,
    val activeBoardId: String? = null,
    val isAllBoardsSelected: Boolean = false,
    val showCrossBoardNodes: Boolean = true,
    val lastOpenedNodeId: String? = null,
    val showEdgeLabels: Boolean = true,
    val showCanvasGrid: Boolean = true,
    val defaultCanvasScale: Float = 1.0f
)

/**
 * 使用 Jetpack DataStore 持久化本地设置项（主题、当前选中画板空间、跨主题节点显示、最后打开节点、网格与边签设置）
 */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val ACTIVE_BOARD_ID = stringPreferencesKey("active_board_id")
        val SHOW_CROSS_BOARD_NODES = booleanPreferencesKey("show_cross_board_nodes")
        val LAST_OPENED_NODE_ID = stringPreferencesKey("last_opened_node_id")
        val SHOW_EDGE_LABELS = booleanPreferencesKey("show_edge_labels")
        val SHOW_CANVAS_GRID = booleanPreferencesKey("show_canvas_grid")
        val DEFAULT_CANVAS_SCALE = floatPreferencesKey("default_canvas_scale")
    }

    val preferencesFlow: Flow<WorkspacePreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val rawBoardId = prefs[Keys.ACTIVE_BOARD_ID]
            val isAllSelected = rawBoardId == ALL_BOARDS_SENTINEL
            WorkspacePreferences(
                isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
                activeBoardId = if (isAllSelected) null else rawBoardId,
                isAllBoardsSelected = isAllSelected,
                showCrossBoardNodes = prefs[Keys.SHOW_CROSS_BOARD_NODES] ?: true,
                lastOpenedNodeId = prefs[Keys.LAST_OPENED_NODE_ID],
                showEdgeLabels = prefs[Keys.SHOW_EDGE_LABELS] ?: true,
                showCanvasGrid = prefs[Keys.SHOW_CANVAS_GRID] ?: true,
                defaultCanvasScale = prefs[Keys.DEFAULT_CANVAS_SCALE] ?: 1.0f
            )
        }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_DARK_THEME] = isDark
        }
    }

    /**
     * 当 boardId == null 时，显式写入 ALL_BOARDS_SENTINEL 表示用户主动选择了「全部主题」，
     * 避免被 ViewModel 误判为未初始化而强制回退到第一个主题画板。
     */
    suspend fun setActiveBoardId(boardId: String?) {
        context.dataStore.edit { prefs ->
            if (boardId.isNullOrBlank()) {
                prefs[Keys.ACTIVE_BOARD_ID] = ALL_BOARDS_SENTINEL
            } else {
                prefs[Keys.ACTIVE_BOARD_ID] = boardId
            }
        }
    }

    suspend fun setShowCrossBoardNodes(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_CROSS_BOARD_NODES] = show
        }
    }

    suspend fun setLastOpenedNodeId(nodeId: String?) {
        context.dataStore.edit { prefs ->
            if (nodeId == null) {
                prefs.remove(Keys.LAST_OPENED_NODE_ID)
            } else {
                prefs[Keys.LAST_OPENED_NODE_ID] = nodeId
            }
        }
    }

    suspend fun setShowEdgeLabels(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_EDGE_LABELS] = show
        }
    }

    suspend fun setShowCanvasGrid(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_CANVAS_GRID] = show
        }
    }
}
