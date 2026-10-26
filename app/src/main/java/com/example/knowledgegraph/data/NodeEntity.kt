package com.example.knowledgegraph.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.knowledgegraph.model.NodeData

/**
 * Room 数据库节点实体表（外键级联绑定 BoardTopicEntity，删除主题画板时自动清理其下节点）
 */
@Entity(
    tableName = "knowledge_nodes",
    foreignKeys = [
        ForeignKey(
            entity = BoardTopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["boardId"])]
)
data class NodeEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val markdownContent: String,
    val tagList: List<String>,
    val color: String,
    val x: Float,
    val y: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val boardId: String
) {
    fun toDomain(): NodeData = NodeData(
        id = id,
        title = title,
        markdownContent = markdownContent,
        tagList = tagList,
        color = color,
        x = x,
        y = y,
        createdAt = createdAt,
        updatedAt = updatedAt,
        boardId = boardId
    )

    companion object {
        fun fromDomain(node: NodeData): NodeEntity = NodeEntity(
            id = node.id,
            title = node.title,
            markdownContent = node.markdownContent,
            tagList = node.tagList,
            color = node.color,
            x = node.x,
            y = node.y,
            createdAt = node.createdAt,
            updatedAt = node.updatedAt,
            boardId = node.boardId
        )
    }
}
