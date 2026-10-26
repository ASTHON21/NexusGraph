package com.example.knowledgegraph.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 本地优先知识图谱 Room 数据库（Version 3：纯净无预置演示数据，含完整级联外键约束）
 */
@Database(
    entities = [BoardTopicEntity::class, NodeEntity::class, EdgeEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class KnowledgeDatabase : RoomDatabase() {

    abstract fun boardDao(): BoardDao
    abstract fun nodeDao(): NodeDao
    abstract fun edgeDao(): EdgeDao

    companion object {
        private const val DATABASE_NAME = "nexus_knowledge_graph.db"

        @Volatile
        private var INSTANCE: KnowledgeDatabase? = null

        fun getInstance(context: Context): KnowledgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KnowledgeDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
