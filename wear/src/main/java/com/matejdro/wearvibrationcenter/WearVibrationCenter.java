package com.matejdro.wearvibrationcenter;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import com.matejdro.wearutils.logging.FileLogger;
import com.matejdro.wearutils.logging.TimberExceptionWear;

import pl.tajchert.exceptionwear.ExceptionWear;
import timber.log.Timber;

public class WearVibrationCenter extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Timber.setAppTag("WearVibrationCenter");

        boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!isDebuggable) {
            ExceptionWear.initialize(this);
            Timber.plant(new TimberExceptionWear(this));
        }

        Timber.plant(new Timber.AndroidDebugTree(isDebuggable));

        FileLogger fileLogger = FileLogger.getInstance(this);
        fileLogger.activate();
        Timber.plant(fileLogger);
    }
}
