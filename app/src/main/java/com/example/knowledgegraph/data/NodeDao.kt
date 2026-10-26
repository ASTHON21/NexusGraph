package com.example.knowledgegraph.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 知识节点 DAO 接口
 */
@Dao
interface NodeDao {

    @Query("SELECT * FROM knowledge_nodes ORDER BY updatedAt DESC")
    fun observeAllNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM knowledge_nodes WHERE id = :nodeId LIMIT 1")
    fun observeNodeById(nodeId: String): Flow<NodeEntity?>

    @Query("SELECT * FROM knowledge_nodes WHERE id = :nodeId LIMIT 1")
    suspend fun getNodeById(nodeId: String): NodeEntity?

    @Query("SELECT COUNT(*) FROM knowledge_nodes")
    suspend fun getNodeCount(): Int

    @Query(
        """
        SELECT * FROM knowledge_nodes 
        WHERE title LIKE '%' || :query || '%' 
           OR markdownContent LIKE '%' || :query || '%' 
           OR tagList LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun searchNodes(query: String): Flow<List<NodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodes(nodes: List<NodeEntity>)

    @Update
    suspend fun updateNode(node: NodeEntity)

    @Query("UPDATE knowledge_nodes SET x = :x, y = :y, updatedAt = :updatedAt WHERE id = :nodeId")
    suspend fun updateNodePosition(nodeId: String, x: Float, y: Float, updatedAt: Long)

    @Query("UPDATE knowledge_nodes SET boardId = :newBoardId, updatedAt = :updatedAt WHERE boardId = :oldBoardId")
    suspend fun reassignNodesBoard(oldBoardId: String, newBoardId: String, updatedAt: Long)

    @Query("DELETE FROM knowledge_nodes WHERE id = :nodeId")
    suspend fun deleteNodeById(nodeId: String)

    @Query("DELETE FROM knowledge_nodes")
    suspend fun deleteAllNodes()
}
