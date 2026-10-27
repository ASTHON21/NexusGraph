package com.example.knowledgegraph.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.RelationType

/**
 * Room 数据库关系边实体表（当父节点删除时级联清理对应边，含单列与复合对端索引）
 */
@Entity(
    tableName = "knowledge_edges",
    foreignKeys = [
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromNodeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["toNodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["fromNodeId"]),
        Index(value = ["toNodeId"]),
        Index(value = ["fromNodeId", "toNodeId"])
    ]
)
data class EdgeEntity(
    @PrimaryKey
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val type: RelationType,
    val label: String,
    val createdAt: Long
) {
    fun toDomain(): EdgeData = EdgeData(
        id = id,
        fromNodeId = fromNodeId,
        toNodeId = toNodeId,
        type = type,
        label = label,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(edge: EdgeData): EdgeEntity = EdgeEntity(
            id = edge.id,
            fromNodeId = edge.fromNodeId,
            toNodeId = edge.toNodeId,
            type = edge.type,
            label = edge.label,
            createdAt = edge.createdAt
        )
    }
}
