package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.knowledgegraph.AppApplication
import com.example.knowledgegraph.di.AppContainer
import com.example.knowledgegraph.navigation.KnowledgeGraphApp

/**
 * 唯一主启动 Activity 入口，消除重复 Activity 定义
 */
class MainActivity : ComponentActivity() {
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
