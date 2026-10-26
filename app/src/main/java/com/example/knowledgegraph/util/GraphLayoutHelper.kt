package com.example.knowledgegraph.util

import androidx.compose.ui.geometry.Offset
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.NodeData
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 纯 Kotlin 2D 图谱布局与几何碰撞计算工具类
 */
object GraphLayoutHelper {

    fun worldToScreen(
        worldX: Float,
        worldY: Float,
        canvasCenter: Offset,
        panOffset: Offset,
        scale: Float
    ): Offset {
        return Offset(
            x = canvasCenter.x + panOffset.x + worldX * scale,
            y = canvasCenter.y + panOffset.y + worldY * scale
        )
    }

    fun screenToWorld(
        screenPos: Offset,
        canvasCenter: Offset,
        panOffset: Offset,
        scale: Float
    ): Offset {
        val safeScale = scale.coerceAtLeast(0.15f)
        return Offset(
            x = (screenPos.x - canvasCenter.x - panOffset.x) / safeScale,
            y = (screenPos.y - canvasCenter.y - panOffset.y) / safeScale
        )
    }

    /**
     * 在当前视野中心附近沿黄金螺旋探测无碰撞空位，确保新建节点始终出现在视野中央且绝不与已有节点重叠
     */
    fun findNonOverlappingSpawnPosition(
        viewportCenterWorld: Offset,
        existingNodes: List<NodeData>,
        minDistanceWorld: Float = 155f
    ): Offset {
        if (existingNodes.isEmpty()) return viewportCenterWorld

        val goldenAngle = 2.3999632f // 137.5 degrees in radians
        for (step in 0..80) {
            val candidate = if (step == 0) {
                viewportCenterWorld
            } else {
                val r = 92f * sqrt(step.toFloat())
                val theta = step * goldenAngle
                Offset(
                    x = (viewportCenterWorld.x + r * cos(theta)).coerceIn(-1500f, 1500f),
                    y = (viewportCenterWorld.y + r * sin(theta)).coerceIn(-1500f, 1500f)
                )
            }
            val hasCollision = existingNodes.any { node ->
                hypot(node.x - candidate.x, node.y - candidate.y) < minDistanceWorld
            }
            if (!hasCollision) {
                return candidate
            }
        }
        return Offset(viewportCenterWorld.x + 60f, viewportCenterWorld.y + 60f)
    }

    fun findNodeAtScreenPosition(
        screenTouch: Offset,
        nodes: List<NodeData>,
        canvasCenter: Offset,
        panOffset: Offset,
        scale: Float,
        baseNodeRadiusPx: Float
    ): NodeData? {
        val hitRadius = max(baseNodeRadiusPx * scale * 1.25f, 40f)
        for (node in nodes.asReversed()) {
            val nodeScreen = worldToScreen(node.x, node.y, canvasCenter, panOffset, scale)
            val dist = hypot(screenTouch.x - nodeScreen.x, screenTouch.y - nodeScreen.y)
            if (dist <= hitRadius) {
                return node
            }
        }
        return null
    }

    fun findEdgeAtScreenPosition(
        screenTouch: Offset,
        edges: List<EdgeData>,
        nodeMap: Map<String, NodeData>,
        canvasCenter: Offset,
        panOffset: Offset,
        scale: Float,
        thresholdPx: Float = 32f
    ): EdgeData? {
        for (edge in edges) {
            val from = nodeMap[edge.fromNodeId] ?: continue
            val to = nodeMap[edge.toNodeId] ?: continue
            val p1 = worldToScreen(from.x, from.y, canvasCenter, panOffset, scale)
            val p2 = worldToScreen(to.x, to.y, canvasCenter, panOffset, scale)
            val dist = pointToSegmentDistance(screenTouch, p1, p2)
            if (dist <= thresholdPx) {
                return edge
            }
        }
        return null
    }

    private fun pointToSegmentDistance(p: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return hypot(p.x - projX, p.y - projY)
    }

    fun computeCircularLayout(nodes: List<NodeData>): Map<String, Pair<Float, Float>> {
        if (nodes.isEmpty()) return emptyMap()
        if (nodes.size == 1) return mapOf(nodes.first().id to (0f to 0f))

        val radius = max(220f, nodes.size * 48f)
        val result = mutableMapOf<String, Pair<Float, Float>>()
        nodes.forEachIndexed { index, node ->
            val angle = (2.0 * PI * index / nodes.size) - PI / 2.0
            val x = (radius * cos(angle)).toFloat()
            val y = (radius * sin(angle)).toFloat()
            result[node.id] = x to y
        }
        return result
    }

    fun computeRadialLayout(
        nodes: List<NodeData>,
        edges: List<EdgeData>,
        centerNodeId: String? = null
    ): Map<String, Pair<Float, Float>> {
        if (nodes.isEmpty()) return emptyMap()
        if (nodes.size == 1) return mapOf(nodes.first().id to (0f to 0f))

        val degreeMap = nodes.associate { node ->
            node.id to edges.count { it.fromNodeId == node.id || it.toNodeId == node.id }
        }
        val hubNode = nodes.firstOrNull { it.id == centerNodeId }
            ?: nodes.maxByOrNull { degreeMap[it.id] ?: 0 }
            ?: nodes.first()

        val result = mutableMapOf<String, Pair<Float, Float>>()
        result[hubNode.id] = 0f to 0f

        val connectedIds = edges.mapNotNull {
            when {
                it.fromNodeId == hubNode.id -> it.toNodeId
                it.toNodeId == hubNode.id -> it.fromNodeId
                else -> null
            }
        }.toSet()

        val innerRing = nodes.filter { it.id != hubNode.id && it.id in connectedIds }
        val outerRing = nodes.filter { it.id != hubNode.id && it.id !in connectedIds }

        val innerRadius = 240f
        innerRing.forEachIndexed { idx, node ->
            val angle = (2.0 * PI * idx / max(1, innerRing.size)) - PI / 2.0
            result[node.id] = (innerRadius * cos(angle)).toFloat() to (innerRadius * sin(angle)).toFloat()
        }

        val outerRadius = if (innerRing.isEmpty()) 260f else 450f
        outerRing.forEachIndexed { idx, node ->
            val angle = (2.0 * PI * idx / max(1, outerRing.size)) - PI / 4.0
            result[node.id] = (outerRadius * cos(angle)).toFloat() to (outerRadius * sin(angle)).toFloat()
        }
        return result
    }

    fun computeForceDirectedLayout(
        nodes: List<NodeData>,
        edges: List<EdgeData>,
        iterations: Int = 65
    ): Map<String, Pair<Float, Float>> {
        if (nodes.isEmpty()) return emptyMap()
        if (nodes.size == 1) return mapOf(nodes.first().id to (0f to 0f))

        val posX = FloatArray(nodes.size) { i -> nodes[i].x }
        val posY = FloatArray(nodes.size) { i -> nodes[i].y }
        val dispX = FloatArray(nodes.size)
        val dispY = FloatArray(nodes.size)
        val idToIndex = nodes.mapIndexed { idx, node -> node.id to idx }.toMap()

        val idealDist = 250f
        var temperature = 180f

        repeat(iterations) {
            dispX.fill(0f)
            dispY.fill(0f)

            for (i in nodes.indices) {
                for (j in i + 1 until nodes.size) {
                    var dx = posX[i] - posX[j]
                    var dy = posY[i] - posY[j]
                    if (dx == 0f && dy == 0f) {
                        dx = (i - j) * 6f
                        dy = (j - i) * 4f
                    }
                    val dist = max(25f, hypot(dx, dy))
                    val force = (idealDist * idealDist) / dist
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    dispX[i] += fx
                    dispY[i] += fy
                    dispX[j] -= fx
                    dispY[j] -= fy
                }
            }

            for (edge in edges) {
                val u = idToIndex[edge.fromNodeId] ?: continue
                val v = idToIndex[edge.toNodeId] ?: continue
                val dx = posX[u] - posX[v]
                val dy = posY[u] - posY[v]
                val dist = max(10f, hypot(dx, dy))
                val force = (dist * dist) / idealDist * 0.45f
                val fx = (dx / dist) * force
                val fy = (dy / dist) * force
                dispX[u] -= fx
                dispY[u] -= fy
                dispX[v] += fx
                dispY[v] += fy
            }

            for (i in nodes.indices) {
                dispX[i] -= posX[i] * 0.08f
                dispY[i] -= posY[i] * 0.08f

                val dispLen = max(1f, hypot(dispX[i], dispY[i]))
                val step = min(dispLen, temperature)
                posX[i] = (posX[i] + (dispX[i] / dispLen) * step).coerceIn(-950f, 950f)
                posY[i] = (posY[i] + (dispY[i] / dispLen) * step).coerceIn(-950f, 950f)
            }

            temperature *= 0.92f
        }

        val avgX = posX.average().toFloat()
        val avgY = posY.average().toFloat()
        return nodes.mapIndexed { idx, node ->
            node.id to ((posX[idx] - avgX) to (posY[idx] - avgY))
        }.toMap()
    }

    fun computeGridLayout(nodes: List<NodeData>): Map<String, Pair<Float, Float>> {
        if (nodes.isEmpty()) return emptyMap()
        val cols = ceil(sqrt(nodes.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(nodes.size.toDouble() / cols).toInt().coerceAtLeast(1)
        val spacingX = 270f
        val spacingY = 210f
        val offsetX = (cols - 1) * spacingX / 2f
        val offsetY = (rows - 1) * spacingY / 2f

        return nodes.mapIndexed { index, node ->
            val r = index / cols
            val c = index % cols
            val x = c * spacingX - offsetX
            val y = r * spacingY - offsetY
            node.id to (x to y)
        }.toMap()
    }

    fun computeArrowHeadPoints(
        start: Offset,
        end: Offset,
        targetRadius: Float,
        arrowLength: Float = 24f,
        arrowAngleRad: Float = 0.48f
    ): Triple<Offset, Offset, Offset>? {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dist = hypot(dx, dy)
        if (dist <= targetRadius + 8f) return null

        val angle = atan2(dy, dx)
        val tip = Offset(
            x = end.x - cos(angle) * targetRadius,
            y = end.y - sin(angle) * targetRadius
        )
        val left = Offset(
            x = tip.x - arrowLength * cos(angle - arrowAngleRad),
            y = tip.y - arrowLength * sin(angle - arrowAngleRad)
        )
        val right = Offset(
            x = tip.x - arrowLength * cos(angle + arrowAngleRad),
            y = tip.y - arrowLength * sin(angle + arrowAngleRad)
        )
        return Triple(tip, left, right)
    }
}
