package com.example.knowledgegraph.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.knowledgegraph.data.KnowledgeDatabase
import com.example.knowledgegraph.data.UserPreferencesRepository
import com.example.knowledgegraph.repository.KnowledgeRepository
import com.example.knowledgegraph.repository.KnowledgeRepositoryImpl
import com.example.knowledgegraph.viewmodel.GraphViewModel
import com.example.knowledgegraph.viewmodel.KnowledgeViewModel
import com.example.knowledgegraph.viewmodel.NodeDetailViewModel

/**
 * 纯 Kotlin 轻量级依赖注入容器（零反射负担，提供 Room Database、DataStore、Repository 与 ViewModel 工厂）
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: KnowledgeDatabase by lazy {
        KnowledgeDatabase.getInstance(appContext)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }

    val knowledgeRepository: KnowledgeRepository by lazy {
        KnowledgeRepositoryImpl(
            context = appContext,
            boardDao = database.boardDao(),
            nodeDao = database.nodeDao(),
            edgeDao = database.edgeDao()
        )
    }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when {
                modelClass.isAssignableFrom(KnowledgeViewModel::class.java) -> {
                    KnowledgeViewModel(knowledgeRepository, preferencesRepository) as T
                }
                modelClass.isAssignableFrom(GraphViewModel::class.java) -> {
                    GraphViewModel(knowledgeRepository) as T
                }
                modelClass.isAssignableFrom(NodeDetailViewModel::class.java) -> {
                    NodeDetailViewModel(knowledgeRepository, preferencesRepository) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
