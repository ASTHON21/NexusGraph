package com.example

import androidx.compose.ui.geometry.Offset
import com.example.knowledgegraph.model.BoardTopic
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.NodeData
import com.example.knowledgegraph.model.RelationType
import com.example.knowledgegraph.util.GraphDomainService
import com.example.knowledgegraph.util.GraphLayoutHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `GraphDomainService exports and parses workspace JSON round-trip accurately`() {
        val boards = listOf(
            BoardTopic(
                id = "board_1",
                name = "架构课题",
                description = "分布式与高可用设计",
                colorHex = "#6366F1",
                createdAt = 1700000000L,
                sortOrder = 0
            ),
            BoardTopic(
                id = "board_2",
                name = "AI 推理",
                description = "模型量化与算子融合",
                colorHex = "#10B981",
                createdAt = 1700000100L,
                sortOrder = 1
            )
        )
        val nodes = listOf(
            NodeData(
                id = "node_a",
                title = "Raft 共识",
                markdownContent = "# Raft 共识\n参考 [[算子优化]] 与 \"引号测试\"",
                tagList = listOf("共识", "分布式"),
                color = "#6366F1",
                x = 120f,
                y = -85f,
                createdAt = 1700000200L,
                updatedAt = 1700000300L,
                boardId = "board_1"
            ),
            NodeData(
                id = "node_b",
                title = "算子优化",
                markdownContent = "## CUDA Kernel\n支持 FlashAttention",
                tagList = listOf("GPU", "推理"),
                color = "#10B981",
                x = -240f,
                y = 160f,
                createdAt = 1700000400L,
                updatedAt = 1700000500L,
                boardId = "board_2"
            )
        )
        val edges = listOf(
            EdgeData(
                id = "edge_1",
                fromNodeId = "node_a",
                toNodeId = "node_b",
                type = RelationType.REFERENCES,
                label = "跨界参考",
                createdAt = 1700000600L
            )
        )

        val json = GraphDomainService.exportWorkspaceToJson(boards, nodes, edges)
        val parsed = GraphDomainService.parseWorkspaceFromJson(json).getOrThrow()

        assertEquals(2, parsed.boards.size)
        assertEquals("架构课题", parsed.boards[0].name)
        assertEquals(2, parsed.nodes.size)
        assertEquals("Raft 共识", parsed.nodes[0].title)
        assertTrue(parsed.nodes[0].markdownContent.contains("\"引号测试\""))
        assertEquals(listOf("共识", "分布式"), parsed.nodes[0].tagList)
        assertEquals(120f, parsed.nodes[0].x, 0.01f)
        assertEquals(1, parsed.edges.size)
        assertEquals(RelationType.REFERENCES, parsed.edges[0].type)
        assertEquals("跨界参考", parsed.edges[0].label)
    }

    @Test
    fun `GraphDomainService computes board overview modules and distinct sector anchors`() {
        val b1 = BoardTopic(id = "b1", name = "主题 1", description = "", colorHex = "#6366F1", createdAt = 1L)
        val b2 = BoardTopic(id = "b2", name = "主题 2", description = "", colorHex = "#0EA5E9", createdAt = 2L)
        val boards = listOf(b1, b2)

        val anchor1 = GraphDomainService.computeBoardAnchorCenter("b1", boards)
        val anchor2 = GraphDomainService.computeBoardAnchorCenter("b2", boards)
        assertNotEquals(anchor1, anchor2)

        val n1 = NodeData("n1", "N1", "", listOf("tag1"), "#6366F1", 0f, 0f, 10L, 20L, "b1")
        val n2 = NodeData("n2", "N2", "", listOf("tag1", "tag2"), "#6366F1", 90f, 0f, 10L, 40L, "b1")
        val n3 = NodeData("n3", "N3", "", listOf("tag3"), "#0EA5E9", 260f, 0f, 10L, 30L, "b2")
        val edges = listOf(
            EdgeData("e1", "n1", "n2", RelationType.DEPENDS_ON, "内部依赖", 50L),
            EdgeData("e2", "n2", "n3", RelationType.EXTENDS, "跨界扩展", 60L)
        )

        val modules = GraphDomainService.buildBoardOverviewModules(
            boards = boards,
            nodes = listOf(n1, n2, n3),
            edges = edges,
            activeBoardId = "b1"
        )

        assertEquals(2, modules.size)
        val m1 = modules.first { it.board.id == "b1" }
        assertTrue(m1.isSelected)
        assertEquals(2, m1.nodeCount)
        assertEquals(1, m1.internalEdgeCount)
        assertEquals(1, m1.crossBoardEdgeCount)
        assertEquals("N2", m1.latestNodeTitle)
    }

    @Test
    fun `GraphLayoutHelper finds non-overlapping spawn positions`() {
        val existing = listOf(
            NodeData("n1", "Origin", "", emptyList(), "#6366F1", 0f, 0f, 1L, 1L, "b1")
        )
        val spawned = GraphLayoutHelper.findNonOverlappingSpawnPosition(
            viewportCenterWorld = Offset.Zero,
            existingNodes = existing,
            minDistanceWorld = 120f
        )
        val dist = kotlin.math.hypot(spawned.x, spawned.y)
        assertTrue("Expected distance >= 120f but got $dist", dist >= 119f)
    }
}
