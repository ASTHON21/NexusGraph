package com.example.knowledgegraph

import android.app.Application
import com.example.knowledgegraph.di.AppContainer

/**
 * 应用程序入口 Application 类，持有单例依赖注入容器 AppContainer
 */
class AppApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
