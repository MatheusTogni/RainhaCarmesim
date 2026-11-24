package com.rainhacarmesim.app

import android.app.Application

class RainhaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
