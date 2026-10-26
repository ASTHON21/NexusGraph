package com.example.knowledgegraph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.knowledgegraph.di.AppContainer
import com.example.knowledgegraph.navigation.KnowledgeGraphApp

/**
 * 位于 com.example.knowledgegraph 包下的主 Activity 入口
 */
open class KnowledgeGraphActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as? AppApplication)?.container
            ?: AppContainer(applicationContext)
        setContent {
            KnowledgeGraphApp(container = appContainer)
        }
    }
}
