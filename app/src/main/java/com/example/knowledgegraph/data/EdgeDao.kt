package com.example.knowledgegraph.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 节点语义关系边 DAO 接口（支持同向关系查重与更新）
 */
@Dao
interface EdgeDao {

    @Query("SELECT * FROM knowledge_edges ORDER BY createdAt DESC")
    fun observeAllEdges(): Flow<List<EdgeEntity>>

    @Query("SELECT * FROM knowledge_edges WHERE fromNodeId = :nodeId OR toNodeId = :nodeId ORDER BY createdAt DESC")
    fun observeEdgesForNode(nodeId: String): Flow<List<EdgeEntity>>

    @Query("SELECT * FROM knowledge_edges WHERE fromNodeId = :fromId AND toNodeId = :toId LIMIT 1")
    suspend fun findEdgeBetween(fromId: String, toId: String): EdgeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdge(edge: EdgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdges(edges: List<EdgeEntity>)

    @Query("DELETE FROM knowledge_edges WHERE id = :edgeId")
    suspend fun deleteEdgeById(edgeId: String)

    @Query("DELETE FROM knowledge_edges WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    suspend fun deleteEdgesForNode(nodeId: String)

    @Query("DELETE FROM knowledge_edges")
    suspend fun deleteAllEdges()
}
