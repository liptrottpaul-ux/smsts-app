package uk.co.asmcoach

import android.app.Application

class AsmCoachApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
