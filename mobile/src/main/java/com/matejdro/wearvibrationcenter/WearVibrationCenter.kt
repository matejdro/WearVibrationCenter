package com.matejdro.wearvibrationcenter

import android.app.Application
import timber.log.Timber
import android.content.pm.ApplicationInfo
import timber.log.Timber.AndroidDebugTree
import com.matejdro.wearutils.logging.FileLogger
import com.matejdro.wearvibrationcenter.notification.VibrationCenterChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WearVibrationCenter : Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.setAppTag("WearVibrationCenter")
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Timber.plant(AndroidDebugTree(isDebuggable))
        val fileLogger = FileLogger.getInstance(this)
        fileLogger.activate()
        Timber.plant(fileLogger)

        VibrationCenterChannels.init(this)
    }
}
