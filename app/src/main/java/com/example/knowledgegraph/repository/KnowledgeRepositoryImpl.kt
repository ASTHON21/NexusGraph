package com.example.knowledgegraph.repository

import android.content.Context
import com.example.knowledgegraph.data.BoardDao
import com.example.knowledgegraph.data.BoardTopicEntity
import com.example.knowledgegraph.data.EdgeDao
import com.example.knowledgegraph.data.EdgeEntity
import com.example.knowledgegraph.data.NodeDao
import com.example.knowledgegraph.data.NodeEntity
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.RelationType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 纯净版知识图谱仓库实现类：
 * 1. 不包含任何硬编码假数据或预置节点，完全由用户真实创建
 * 2. 支持主题画板级联删除（同步清理本地 .md 文件副本）
 * 3. 保存节点时自动扫描正文中 [[双链]] 引用并同步建立 REFERENCES 语义边
 * 4. 同一对节点间的有向关系支持去重更新与编辑
 */
class KnowledgeRepositoryImpl(
    private val context: Context,
    private val boardDao: BoardDao,
    private val nodeDao: NodeDao,
    private val edgeDao: EdgeDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : KnowledgeRepository {

    private val markdownDir: File by lazy {
        File(context.filesDir, "markdown_nodes").apply {
            if (!exists()) mkdirs()
        }
    }

    override fun observeAllBoards(): Flow<List<BoardTopic>> {
        return boardDao.observeAllBoards().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeAllNodes(): Flow<List<NodeData>> {
        return nodeDao.observeAllNodes().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeNodeById(nodeId: String): Flow<NodeData?> {
        return nodeDao.observeNodeById(nodeId).map { it?.toDomain() }
    }

    override fun observeAllEdges(): Flow<List<EdgeData>> {
        return edgeDao.observeAllEdges().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeEdgesForNode(nodeId: String): Flow<List<EdgeData>> {
        return edgeDao.observeEdgesForNode(nodeId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getNodeById(nodeId: String): NodeData? = withContext(ioDispatcher) {
        nodeDao.getNodeById(nodeId)?.toDomain()
    }

    override suspend fun createBoard(
        name: String,
        description: String,
        colorHex: String
    ): BoardTopic = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val count = boardDao.getBoardCount()
        val board = BoardTopic(
            id = "board_${UUID.randomUUID().toString().take(8)}",
            name = name.trim().ifEmpty { "未命名主题" },
            description = description.trim().ifEmpty { "课题知识空间" },
            colorHex = colorHex,
            createdAt = now,
            sortOrder = count
        )
        boardDao.upsertBoard(BoardTopicEntity.fromDomain(board))
        board
    }

    override suspend fun saveBoard(board: BoardTopic) = withContext(ioDispatcher) {
        boardDao.upsertBoard(
            BoardTopicEntity.fromDomain(
                board.copy(name = board.name.trim().ifEmpty { "未命名主题" })
            )
        )
    }

    override suspend fun deleteBoard(boardId: String) = withContext(ioDispatcher) {
        // 先清理该主题下所有节点对应的本地 .md 镜像文件，随后通过 Room CASCADE 外键删除主题、节点与关联边
        val nodesInBoard = nodeDao.observeAllNodes().first().filter { it.boardId == boardId }
        nodesInBoard.forEach { deleteMarkdownSnapshotFile(it.id) }
        boardDao.deleteBoardById(boardId)
    }

    override suspend fun createNode(
        title: String,
        markdownContent: String,
        tagList: List<String>,
        color: String,
        x: Float,
        y: Float,
        boardId: String
    ): NodeData = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifEmpty { "未命名节点" }
        val node = NodeData(
            id = "node_${UUID.randomUUID().toString().take(8)}",
            title = cleanTitle,
            markdownContent = markdownContent,
            tagList = tagList.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            color = color,
            x = x,
            y = y,
            createdAt = now,
            updatedAt = now,
            boardId = boardId
        )
        nodeDao.upsertNode(NodeEntity.fromDomain(node))
        writeMarkdownSnapshotToFile(node)
        syncWikiLinkEdges(node)
        node
    }

    override suspend fun saveNode(node: NodeData) = withContext(ioDispatcher) {
        val updated = node.copy(
            title = node.title.trim().ifEmpty { "未命名节点" },
            tagList = node.tagList.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            updatedAt = System.currentTimeMillis()
        )
        nodeDao.upsertNode(NodeEntity.fromDomain(updated))
        writeMarkdownSnapshotToFile(updated)
        syncWikiLinkEdges(updated)
    }

    /**
     * 自动扫描节点 Markdown 正文中的 [[目标节点名]]，若目标节点已存在且尚无有向连接，自动补齐一条「引用」边
     */
    private suspend fun syncWikiLinkEdges(sourceNode: NodeData) {
        val wikiTitles = sourceNode.wikiLinks
        if (wikiTitles.isEmpty()) return
        val allNodes = nodeDao.observeAllNodes().first()
        for (wikiTitle in wikiTitles) {
            val target = allNodes.firstOrNull {
                it.id != sourceNode.id && it.title.equals(wikiTitle, ignoreCase = true)
            } ?: continue
            val existing = edgeDao.findEdgeBetween(sourceNode.id, target.id)
            if (existing == null) {
                val edge = EdgeData(
                    id = "edge_${UUID.randomUUID().toString().take(8)}",
                    fromNodeId = sourceNode.id,
                    toNodeId = target.id,
                    type = RelationType.REFERENCES,
                    label = "双链引用",
                    createdAt = System.currentTimeMillis()
                )
                edgeDao.upsertEdge(EdgeEntity.fromDomain(edge))
            }
        }
    }

    override suspend fun updateNodePosition(nodeId: String, x: Float, y: Float) =
        withContext(ioDispatcher) {
            nodeDao.updateNodePosition(
                nodeId = nodeId,
                x = x,
                y = y,
                updatedAt = System.currentTimeMillis()
            )
        }

    override suspend fun updateMultipleNodePositions(positions: Map<String, Pair<Float, Float>>) =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            positions.forEach { (nodeId, coords) ->
                nodeDao.updateNodePosition(
                    nodeId = nodeId,
                    x = coords.first,
                    y = coords.second,
                    updatedAt = now
                )
            }
        }

    override suspend fun deleteNode(nodeId: String) = withContext(ioDispatcher) {
        edgeDao.deleteEdgesForNode(nodeId)
        nodeDao.deleteNodeById(nodeId)
        deleteMarkdownSnapshotFile(nodeId)
    }

    override suspend fun createOrUpdateEdge(
        fromNodeId: String,
        toNodeId: String,
        type: RelationType,
        label: String
    ): EdgeData? = withContext(ioDispatcher) {
        if (fromNodeId == toNodeId) return@withContext null
        val existing = edgeDao.findEdgeBetween(fromNodeId, toNodeId)
        val cleanLabel = label.trim().ifEmpty { type.labelCn }
        val edge = if (existing != null) {
            EdgeData(
                id = existing.id,
                fromNodeId = fromNodeId,
                toNodeId = toNodeId,
                type = type,
                label = cleanLabel,
                createdAt = existing.createdAt
            )
        } else {
            EdgeData(
                id = "edge_${UUID.randomUUID().toString().take(8)}",
                fromNodeId = fromNodeId,
                toNodeId = toNodeId,
                type = type,
                label = cleanLabel,
                createdAt = System.currentTimeMillis()
            )
        }
        edgeDao.upsertEdge(EdgeEntity.fromDomain(edge))
        edge
    }

    override suspend fun updateEdge(edge: EdgeData) = withContext(ioDispatcher) {
        val cleaned = edge.copy(label = edge.label.trim().ifEmpty { edge.type.labelCn })
        edgeDao.upsertEdge(EdgeEntity.fromDomain(cleaned))
    }

    override suspend fun deleteEdge(edgeId: String) = withContext(ioDispatcher) {
        edgeDao.deleteEdgeById(edgeId)
    }

    private fun writeMarkdownSnapshotToFile(node: NodeData) {
        runCatching {
            val file = File(markdownDir, "${node.id}.md")
            val frontMatter = buildString {
                appendLine("---")
                appendLine("id: ${node.id}")
                appendLine("boardId: ${node.boardId}")
                appendLine("title: \"${node.title.replace("\"", "\\\"")}\"")
                appendLine("tags: [${node.tagList.joinToString(", ")}]")
                appendLine("color: \"${node.color}\"")
                appendLine("position: [${node.x}, ${node.y}]")
                appendLine("updatedAt: ${node.updatedAt}")
                appendLine("---")
                appendLine()
                append(node.markdownContent)
            }
            file.writeText(frontMatter, Charsets.UTF_8)
        }
    }

    private fun deleteMarkdownSnapshotFile(nodeId: String) {
        runCatching {
            File(markdownDir, "$nodeId.md").delete()
        }
    }
}
