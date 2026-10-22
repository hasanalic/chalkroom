package app.chalkroom

import android.app.Application
import app.chalkroom.di.appModule
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ChalkroomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        startKoin {
            androidContext(this@ChalkroomApp)
            modules(appModule)
        }
    }
}
