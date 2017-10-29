package com.matejdro.wearvibrationcenter;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import com.crashlytics.android.Crashlytics;
import com.matejdro.wearutils.logging.FileLogger;
import com.matejdro.wearvibrationcenter.logging.CrashlyticsExceptionWearHandler;
import com.matejdro.wearvibrationcenter.logging.TimberCrashlytics;
import com.matejdro.wearvibrationcenter.notification.VibrationCenterChannels;

import io.fabric.sdk.android.Fabric;
import pl.tajchert.exceptionwear.ExceptionDataListenerService;
import timber.log.Timber;

public class WearVibrationCenter extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Timber.setAppTag("WearVibrationCenter");

        boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!isDebuggable) {
            Fabric.with(this, new Crashlytics());
            Timber.plant(new TimberCrashlytics());
            ExceptionDataListenerService.setHandler(new CrashlyticsExceptionWearHandler());
        }

        Timber.plant(new Timber.AndroidDebugTree(isDebuggable));

        FileLogger fileLogger = FileLogger.getInstance(this);
        fileLogger.activate();
        Timber.plant(fileLogger);

        VibrationCenterChannels.init(this);
    }
}
