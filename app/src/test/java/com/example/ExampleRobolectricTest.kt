package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.util.GraphDomainService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NexusGraph", appName)
    }

    @Test
    fun `NodeData extracts wikiLinks and GraphDomainService builds cross-board UI states`() {
        val b1 = BoardTopic("b1", "架构", "系统架构", "#6366F1", 100L, 0)
        val b2 = BoardTopic("b2", "算法", "核心算法", "#10B981", 200L, 1)
        val boardMap = mapOf(b1.id to b1, b2.id to b2)

        val n1 = NodeData(
            id = "n1",
            title = "分布式存储",
            markdownContent = "结合 [[一致性哈希]] 与 [[Raft 协议]]",
            tagList = listOf("存储"),
            color = "#6366F1",
            x = 0f,
            y = 0f,
            createdAt = 100L,
            updatedAt = 200L,
            boardId = "b1"
        )
        val n2 = NodeData(
            id = "n2",
            title = "一致性哈希",
            markdownContent = "# 一致性哈希",
            tagList = listOf("哈希"),
            color = "#10B981",
            x = 180f,
            y = 40f,
            createdAt = 100L,
            updatedAt = 300L,
            boardId = "b2"
        )

        assertEquals(listOf("一致性哈希", "Raft 协议"), n1.wikiLinks)

        val nodeUiList = GraphDomainService.buildNodeUiList(
            nodes = listOf(n1, n2),
            edges = emptyList(),
            boardMap = boardMap,
            activeBoardId = "b1",
            activeSelectedId = "n1"
        )
        assertEquals(2, nodeUiList.size)
        val ui1 = nodeUiList.first { it.node.id == "n1" }
        val ui2 = nodeUiList.first { it.node.id == "n2" }
        assertTrue(!ui1.isCrossBoard)
        assertTrue(ui2.isCrossBoard)
        assertEquals(RelationType.REFERENCES, RelationType.fromName("references"))
    }
}
