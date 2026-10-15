package dev.wolzard.recon

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.wolzard.recon.work.Visible
import dev.wolzard.recon.data.media.YtDlp
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
        YtDlpUpdateWorker.sync(this, YtDlp.autoUpdate(this)) // also cancels the job older versions always scheduled
        // Share sheet: a finished job pops up only when nothing of Recon is on screen (ResultPresenter).
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { Visible.resumedActivities++ }
            override fun onActivityPaused(a: Activity) { Visible.resumedActivities-- }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }
}
