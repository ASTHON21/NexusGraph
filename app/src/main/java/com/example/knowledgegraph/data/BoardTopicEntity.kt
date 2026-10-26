package com.example.knowledgegraph.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.knowledgegraph.model.BoardTopic

/**
 * Room 数据库主题画板实体表
 */
@Entity(tableName = "knowledge_boards")
data class BoardTopicEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val colorHex: String,
    val createdAt: Long,
    val sortOrder: Int
) {
    fun toDomain(): BoardTopic = BoardTopic(
        id = id,
        name = name,
        description = description,
        colorHex = colorHex,
        createdAt = createdAt,
        sortOrder = sortOrder
    )

    companion object {
        fun fromDomain(board: BoardTopic): BoardTopicEntity = BoardTopicEntity(
            id = board.id,
            name = board.name,
            description = board.description,
            colorHex = board.colorHex,
            createdAt = board.createdAt,
            sortOrder = board.sortOrder
        )
    }
}
