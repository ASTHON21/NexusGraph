package com.example.knowledgegraph.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间格式化工具类
 */
object DateTimeFormatter {

    fun formatRelativeOrShort(timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = (now - timestampMs).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L

        return when {
            minutes < 1 -> "刚刚更新"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days < 7 -> "${days}天前"
            else -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                sdf.format(Date(timestampMs))
            }
        }
    }

    fun formatFullDateTime(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}
