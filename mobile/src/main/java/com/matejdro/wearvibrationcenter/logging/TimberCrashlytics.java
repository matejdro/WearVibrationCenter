package com.matejdro.wearvibrationcenter.logging;

import com.crashlytics.android.Crashlytics;

import timber.log.Timber;

public class TimberCrashlytics extends Timber.Tree {
    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
        if (t != null) {
            Crashlytics.log(priority, tag, message);
            Crashlytics.logException(t);
        }
    }
}
