package com.matejdro.wearvibrationcenter.logging;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.wearable.DataMap;

import pl.tajchert.exceptionwear.ExceptionWearHandler;
import timber.log.Timber;

/**
 * Sample Handler to show how to handle exception from Wear with use of Crashlytics.
 */
public class CrashlyticsExceptionWearHandler implements ExceptionWearHandler{
    @Override
    /**
     * Exception handler with Crashlytics support, also it will be shown in logcat.
     */
    public void handleException(Throwable throwable, DataMap map) {
        Timber.d("HandleException %s", throwable);
        Crashlytics.setBool("wear_exception", true);
        Crashlytics.setString("board", map.getString("board"));
        Crashlytics.setString("fingerprint", map.getString("fingerprint"));
        Crashlytics.setString("model", map.getString("model"));
        Crashlytics.setString("manufacturer", map.getString("manufacturer"));
        Crashlytics.setString("product", map.getString("product"));
        Crashlytics.setString("api_level", map.getString("api_level"));

        Crashlytics.logException(throwable);
    }
}