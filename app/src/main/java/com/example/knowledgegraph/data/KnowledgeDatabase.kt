package com.example.knowledgegraph.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 本地优先知识图谱 Room 数据库（Version 4：支持无损索引迁移与级联外键约束）
 */
@Database(
    entities = [BoardTopicEntity::class, NodeEntity::class, EdgeEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class KnowledgeDatabase : RoomDatabase() {

    abstract fun boardDao(): BoardDao
    abstract fun nodeDao(): NodeDao
    abstract fun edgeDao(): EdgeDao

    companion object {
        private const val DATABASE_NAME = "nexus_knowledge_graph.db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_knowledge_nodes_updatedAt` ON `knowledge_nodes` (`updatedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_knowledge_edges_fromNodeId_toNodeId` ON `knowledge_edges` (`fromNodeId`, `toNodeId`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: KnowledgeDatabase? = null

        fun getInstance(context: Context): KnowledgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KnowledgeDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
