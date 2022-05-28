package com.matejdro.wearvibrationcenter;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import com.matejdro.wearutils.logging.FileLogger;
import com.matejdro.wearvibrationcenter.notification.VibrationCenterChannels;

import pl.tajchert.exceptionwear.ExceptionDataListenerService;
import timber.log.Timber;

public class WearVibrationCenter extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Timber.setAppTag("WearVibrationCenter");

        boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        Timber.plant(new Timber.AndroidDebugTree(isDebuggable));

        FileLogger fileLogger = FileLogger.getInstance(this);
        fileLogger.activate();
        Timber.plant(fileLogger);

        VibrationCenterChannels.init(this);
    }
}
