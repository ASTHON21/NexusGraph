package com.example.knowledgegraph.util

import androidx.compose.ui.graphics.Color
import com.example.knowledgegraph.model.RelationType

/**
 * 节点色彩与关系色彩工具类（提供明亮/深色模式高对比度配色）
 */
object NodeColorUtil {

    data class NodePaletteOption(
        val name: String,
        val hex: String,
        val color: Color
    )

    val paletteOptions: List<NodePaletteOption> = listOf(
        NodePaletteOption("星云靛", "#6366F1", Color(0xFF6366F1)),
        NodePaletteOption("极光青", "#0EA5E9", Color(0xFF0EA5E9)),
        NodePaletteOption("翡翠绿", "#10B981", Color(0xFF10B981)),
        NodePaletteOption("琥珀金", "#F59E0B", Color(0xFFF59E0B)),
        NodePaletteOption("珊瑚红", "#F43F5E", Color(0xFFF43F5E)),
        NodePaletteOption("幻影紫", "#8B5CF6", Color(0xFF8B5CF6)),
        NodePaletteOption("碧海蓝", "#14B8A6", Color(0xFF14B8A6)),
        NodePaletteOption("星芒粉", "#EC4899", Color(0xFFEC4899))
    )

    fun parseColor(hexString: String, fallback: Color = Color(0xFF6366F1)): Color {
        return try {
            val cleaned = hexString.trim().removePrefix("#")
            when (cleaned.length) {
                6 -> Color(("FF$cleaned").toLong(16))
                8 -> Color(cleaned.toLong(16))
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    fun relationColor(type: RelationType): Color {
        return parseColor(type.colorHex, Color(0xFF38BDF8))
    }
}
