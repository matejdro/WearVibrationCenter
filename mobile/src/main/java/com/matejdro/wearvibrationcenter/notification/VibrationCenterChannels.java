package com.matejdro.wearvibrationcenter.notification;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.matejdro.wearvibrationcenter.R;

@TargetApi(Build.VERSION_CODES.O)
public class VibrationCenterChannels {
    public static String CHANNEL_TEMPORARY_MUTE = "TEMPORARY_MUTE";

    public static void init(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_TEMPORARY_MUTE,
                context.getString(R.string.temporary_mute_channel_name),
                NotificationManager.IMPORTANCE_MIN
        );
        notificationManager.createNotificationChannel(channel);

    }
}
