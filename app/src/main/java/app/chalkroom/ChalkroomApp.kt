package app.chalkroom

import android.app.Application
import app.chalkroom.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ChalkroomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ChalkroomApp)
            modules(appModule)
        }
    }
}
