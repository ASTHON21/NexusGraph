package com.example.knowledgegraph.repository

import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.util.WorkspaceBackupPayload
import kotlinx.coroutines.flow.Flow

/**
 * 知识图谱核心仓库接口：管理主题画板、知识节点、语义关系（含去重与编辑）、本地 Markdown 文件同步与全量 JSON 备份导入
 */
interface KnowledgeRepository {

    fun observeAllBoards(): Flow<List<BoardTopic>>

    fun observeAllNodes(): Flow<List<NodeData>>

    fun observeNodeById(nodeId: String): Flow<NodeData?>

    fun observeAllEdges(): Flow<List<EdgeData>>

    fun observeEdgesForNode(nodeId: String): Flow<List<EdgeData>>

    suspend fun getNodeById(nodeId: String): NodeData?

    suspend fun createBoard(
        name: String,
        description: String,
        colorHex: String
    ): BoardTopic

    suspend fun saveBoard(board: BoardTopic)

    suspend fun deleteBoard(boardId: String)

    suspend fun createNode(
        title: String,
        markdownContent: String,
        tagList: List<String>,
        color: String,
        x: Float,
        y: Float,
        boardId: String
    ): NodeData

    suspend fun saveNode(node: NodeData)

    suspend fun updateNodePosition(nodeId: String, x: Float, y: Float)

    suspend fun updateMultipleNodePositions(positions: Map<String, Pair<Float, Float>>)

    suspend fun deleteNode(nodeId: String)

    suspend fun createOrUpdateEdge(
        fromNodeId: String,
        toNodeId: String,
        type: RelationType,
        label: String
    ): EdgeData?

    suspend fun updateEdge(edge: EdgeData)

    suspend fun deleteEdge(edgeId: String)

    suspend fun importWorkspaceBackup(payload: WorkspaceBackupPayload): Int
}
