package dev.wolzard.recon

import android.app.Application
import dev.wolzard.recon.di.appModule
import dev.wolzard.recon.work.YtDlpUpdateWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ReconApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ReconApp)
            modules(appModule)
        }
        YtDlpUpdateWorker.schedule(this)
    }
}
