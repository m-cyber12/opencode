package dev.opencode.android

import android.app.Application

class OpenCodeApp : Application() {

    lateinit var container: AppContainer
        private set

    companion object {
        @Volatile
        private var instance: OpenCodeApp? = null

        fun get(): OpenCodeApp = instance ?: throw IllegalStateException("App not created")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }
}
