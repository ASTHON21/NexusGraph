package com.example.knowledgegraph.util

import androidx.compose.ui.geometry.Offset
import com.example.knowledgegraph.model.EdgeData
import com.example.knowledgegraph.model.NodeData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 纯 Kotlin 2D 图谱布局与几何防碰撞计算引擎
 * - 针对带底部标题胶囊的节点圆盘，采用宽裕椭球/胶囊安全间距（minDistanceWorld = 210f）
 * - 所有自动排布算法（力导、辐射、环形、网格）均在末尾执行防重叠微调并严格居中至世界原点 (0, 0)
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
     * 在基准锚点附近沿黄金螺旋探测无碰撞空位，同时兼顾节点圆盘与底部标题胶囊的横向/纵向安全距离
     */
    fun findNonOverlappingSpawnPosition(
        viewportCenterWorld: Offset,
        existingNodes: List<NodeData>,
        minDistanceWorld: Float = 210f
    ): Offset {
        if (existingNodes.isEmpty()) return viewportCenterWorld

        val goldenAngle = 2.3999632f // 137.5 degrees in radians
        val minBoxX = max(minDistanceWorld, 205f)
        val minBoxY = max(minDistanceWorld * 0.82f, 165f)

        for (step in 0..140) {
            val candidate = if (step == 0) {
                viewportCenterWorld
            } else {
                val r = 135f * sqrt(step.toFloat())
                val theta = step * goldenAngle
                Offset(
                    x = (viewportCenterWorld.x + r * cos(theta)).coerceIn(-2600f, 2600f),
                    y = (viewportCenterWorld.y + r * sin(theta)).coerceIn(-2600f, 2600f)
                )
            }
            val hasCollision = existingNodes.any { node ->
                val dx = abs(node.x - candidate.x)
                val dy = abs(node.y - candidate.y)
                hypot(dx, dy) < minDistanceWorld || (dx < minBoxX && dy < minBoxY)
            }
            if (!hasCollision) {
                return candidate
            }
        }
        return Offset(viewportCenterWorld.x + minDistanceWorld, viewportCenterWorld.y)
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

        // 确保圆周弧长足以容纳每个节点的底部标题胶囊
        val minChord = 225f
        val radiusByChord = (minChord * nodes.size / (2.0 * PI)).toFloat()
        val radius = max(230f, radiusByChord)
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

        val innerRadius = max(250f, innerRing.size * 38f)
        innerRing.forEachIndexed { idx, node ->
            val angle = (2.0 * PI * idx / max(1, innerRing.size)) - PI / 2.0
            result[node.id] = (innerRadius * cos(angle)).toFloat() to (innerRadius * sin(angle)).toFloat()
        }

        val baseOuterRadius = if (innerRing.isEmpty()) 260f else innerRadius + 240f
        val outerRadius = max(baseOuterRadius, outerRing.size * 36f)
        outerRing.forEachIndexed { idx, node ->
            val angle = (2.0 * PI * idx / max(1, outerRing.size)) - PI / 4.0
            result[node.id] = (outerRadius * cos(angle)).toFloat() to (outerRadius * sin(angle)).toFloat()
        }
        return resolveCollisionsAndCenter(result)
    }

    fun computeForceDirectedLayout(
        nodes: List<NodeData>,
        edges: List<EdgeData>,
        iterations: Int = 75
    ): Map<String, Pair<Float, Float>> {
        if (nodes.isEmpty()) return emptyMap()
        if (nodes.size == 1) return mapOf(nodes.first().id to (0f to 0f))

        // 以均匀环形作为初始种子混合原坐标，彻底避免共线或贴墙排成竖列
        val initRadius = max(220f, nodes.size * 34f)
        val avgInitX = nodes.map { it.x }.average().toFloat()
        val avgInitY = nodes.map { it.y }.average().toFloat()
        val posX = FloatArray(nodes.size) { i ->
            val seedAngle = 2.0 * PI * i / nodes.size
            val seedX = (initRadius * cos(seedAngle)).toFloat()
            (nodes[i].x - avgInitX) * 0.35f + seedX * 0.65f
        }
        val posY = FloatArray(nodes.size) { i ->
            val seedAngle = 2.0 * PI * i / nodes.size
            val seedY = (initRadius * sin(seedAngle)).toFloat()
            (nodes[i].y - avgInitY) * 0.35f + seedY * 0.65f
        }
        val dispX = FloatArray(nodes.size)
        val dispY = FloatArray(nodes.size)
        val idToIndex = nodes.mapIndexed { idx, node -> node.id to idx }.toMap()

        val idealDist = 270f
        var temperature = 210f

        repeat(iterations) {
            dispX.fill(0f)
            dispY.fill(0f)

            for (i in nodes.indices) {
                for (j in i + 1 until nodes.size) {
                    var dx = posX[i] - posX[j]
                    var dy = posY[i] - posY[j]
                    if (abs(dx) < 1f && abs(dy) < 1f) {
                        val angle = (i * 1.7 + j * 0.9)
                        dx = (cos(angle) * 12.0).toFloat()
                        dy = (sin(angle) * 12.0).toFloat()
                    }
                    val dist = max(30f, hypot(dx, dy))
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
                val dist = max(16f, hypot(dx, dy))
                val force = (dist * dist) / idealDist * 0.42f
                val fx = (dx / dist) * force
                val fy = (dy / dist) * force
                dispX[u] -= fx
                dispY[u] -= fy
                dispX[v] += fx
                dispY[v] += fy
            }

            // 温和向心引力，防止孤立子图漂移过远，但不设硬性 950f 贴墙截断
            for (i in nodes.indices) {
                dispX[i] -= posX[i] * 0.06f
                dispY[i] -= posY[i] * 0.06f

                val dispLen = max(1f, hypot(dispX[i], dispY[i]))
                val step = min(dispLen, temperature)
                posX[i] = (posX[i] + (dispX[i] / dispLen) * step).coerceIn(-2400f, 2400f)
                posY[i] = (posY[i] + (dispY[i] / dispLen) * step).coerceIn(-2400f, 2400f)
            }

            temperature *= 0.93f
        }

        val rawMap = nodes.mapIndexed { idx, node ->
            node.id to (posX[idx] to posY[idx])
        }.toMap()
        return resolveCollisionsAndCenter(rawMap)
    }

    fun computeGridLayout(nodes: List<NodeData>): Map<String, Pair<Float, Float>> {
        if (nodes.isEmpty()) return emptyMap()
        val cols = ceil(sqrt(nodes.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(nodes.size.toDouble() / cols).toInt().coerceAtLeast(1)
        val spacingX = 275f
        val spacingY = 215f
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

    /**
     * 对任意布局结果执行防重叠推开迭代，并将几何包围盒中心对齐到世界原点 (0, 0)
     */
    private fun resolveCollisionsAndCenter(
        positions: Map<String, Pair<Float, Float>>,
        minDist: Float = 215f
    ): Map<String, Pair<Float, Float>> {
        if (positions.size <= 1) {
            return positions.keys.firstOrNull()?.let { mapOf(it to (0f to 0f)) } ?: emptyMap()
        }
        val keys = positions.keys.toList()
        val xs = FloatArray(keys.size) { idx -> positions[keys[idx]]!!.first }
        val ys = FloatArray(keys.size) { idx -> positions[keys[idx]]!!.second }

        repeat(24) {
            var moved = false
            for (i in keys.indices) {
                for (j in i + 1 until keys.size) {
                    var dx = xs[i] - xs[j]
                    var dy = ys[i] - ys[j]
                    val dist = hypot(dx, dy)
                    if (dist < minDist) {
                        moved = true
                        if (dist < 1f) {
                            val angle = (i * 1.3 + j * 0.7)
                            dx = cos(angle).toFloat()
                            dy = sin(angle).toFloat()
                        } else {
                            dx /= dist
                            dy /= dist
                        }
                        val push = (minDist - dist) * 0.52f
                        xs[i] += dx * push
                        ys[i] += dy * push
                        xs[j] -= dx * push
                        ys[j] -= dy * push
                    }
                }
            }
            if (!moved) return@repeat
        }

        val minX = xs.minOrNull() ?: 0f
        val maxX = xs.maxOrNull() ?: 0f
        val minY = ys.minOrNull() ?: 0f
        val maxY = ys.maxOrNull() ?: 0f
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        return keys.mapIndexed { idx, id ->
            id to ((xs[idx] - centerX) to (ys[idx] - centerY))
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
