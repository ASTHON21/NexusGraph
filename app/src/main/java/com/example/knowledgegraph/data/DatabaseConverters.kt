package com.example.knowledgegraph.data

import androidx.room.TypeConverter
import com.example.knowledgegraph.model.RelationType

/**
 * Room 类型转换器：纯 Kotlin 实现 List<String> 与 RelationType 持久化转换
 */
class DatabaseConverters {

    private val separator = "\u001F" // Unit Separator 避免与常规标签字符冲突

    @TypeConverter
    fun fromTagList(tags: List<String>?): String {
        if (tags.isNullOrEmpty()) return ""
        return tags.map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator)
    }

    @TypeConverter
    fun toTagList(serialized: String?): List<String> {
        if (serialized.isNullOrBlank()) return emptyList()
        return serialized.split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromRelationType(type: RelationType?): String {
        return (type ?: RelationType.RELATED).name
    }

    @TypeConverter
    fun toRelationType(value: String?): RelationType {
        return if (value.isNullOrBlank()) {
            RelationType.RELATED
        } else {
            RelationType.fromName(value)
        }
    }
}
