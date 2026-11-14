package com.familylock.launcher

import android.app.Application
import com.familylock.launcher.data.LockManager

/** Application class: loads the saved settings before any activity/service/receiver runs. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LockManager.init(this)
    }
}
