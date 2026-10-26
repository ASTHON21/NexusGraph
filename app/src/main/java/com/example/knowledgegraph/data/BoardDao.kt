package com.example.knowledgegraph.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 主题画板空间 DAO 接口
 */
@Dao
interface BoardDao {

    @Query("SELECT * FROM knowledge_boards ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAllBoards(): Flow<List<BoardTopicEntity>>

    @Query("SELECT COUNT(*) FROM knowledge_boards")
    suspend fun getBoardCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBoard(board: BoardTopicEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBoards(boards: List<BoardTopicEntity>)

    @Query("DELETE FROM knowledge_boards WHERE id = :boardId")
    suspend fun deleteBoardById(boardId: String)

    @Query("DELETE FROM knowledge_boards")
    suspend fun deleteAllBoards()
}
