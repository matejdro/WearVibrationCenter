package com.matejdro.wearvibrationcenter.mute;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearutils.messages.ParcelPacker;
import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearvibrationcenter.common.TimedMuteCommand;
import com.matejdro.wearvibrationcenter.notification.NotificationService;
import com.matejdro.wearvibrationcenter.notification.VibrationCenterChannels;
import com.matejdro.wearvibrationcenter.preferences.GlobalSettings;
import com.matejdro.wearvibrationcenter.preferences.ZenModeChange;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import timber.log.Timber;


public class TimedMuteManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {

    private static final int    NOTIFICATION_ID_MUTE_DURATION = 0;
    private static final String ACTON_UNMUTE                  = "com.matejdro.wearvibrationcenter.action.UNMUTE";

    private final GoogleApiClient             googleApiClient;
    private final BroadcastReceiver           unmuteReceiver;
    private final Context                     context;
    private final NotificationListenerService notificationService;
    private final SharedPreferences           preferences;
    private final AlarmManager                alarmManager;

    private boolean mutedCurrently  = false;
    private int     previousZenMode = 0;

    private final PendingIntent unmutePendingIntent;


    @Inject
    public TimedMuteManager(@ApplicationContext Context context, NotificationListenerService service, SharedPreferences preferences) {
        this.notificationService = service;
        this.context = context;
        this.preferences = preferences;

        googleApiClient = new GoogleApiClient.Builder(service).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        googleApiClient.connect();

        unmuteReceiver = new UnmuteReceiver();
        service.registerReceiver(unmuteReceiver, new IntentFilter(ACTON_UNMUTE));

        alarmManager = (AlarmManager) service.getSystemService(Context.ALARM_SERVICE);

        unmutePendingIntent = PendingIntent.getBroadcast(service,
                                                         0,
                                                         new Intent(ACTON_UNMUTE),
                                                         PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public void onDestroy() {
        unmute();

        if (googleApiClient.isConnected()) {
            Wearable.MessageApi.removeListener(googleApiClient, this);
            googleApiClient.disconnect();
        }

        context.unregisterReceiver(unmuteReceiver);
        alarmManager.cancel(unmutePendingIntent);
    }

    public boolean isMutedCurrently() {
        return mutedCurrently;
    }

    private void mute(TimedMuteCommand timedMuteCommand) {
        long mutedUntil = -1;

        if (timedMuteCommand.getMuteDurationMinutes() > 0) {
            mutedUntil = System.currentTimeMillis() + timedMuteCommand.getMuteDurationMinutes() * 1000 * 60;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                                                                            VibrationCenterChannels.CHANNEL_TEMPORARY_MUTE).setSmallIcon(R.drawable.ic_notification)
                                                                                                                           .setPriority(Notification.PRIORITY_MIN)
                                                                                                                           .setDeleteIntent(unmutePendingIntent)
                                                                                                                           .setContentTitle(context.getString(R.string.vibrations_muted_notification_title))
                                                                                                                           .setContentText(context.getString(R.string.vibrations_muted_notification_explanation));

        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();
        wearableExtender.addAction(new NotificationCompat.Action(R.drawable.ic_dismiss_mute, context.getString(R.string.cancel_mute), unmutePendingIntent));
        builder.extend(wearableExtender);

        if (mutedUntil > 0) {
            builder.setWhen(mutedUntil).setUsesChronometer(true);

            // NotificationCompat is missing setChronometerCountDown(), lets do it manually.
            builder.getExtras().putBoolean("android.chronometerCountDown", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, mutedUntil, unmutePendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, mutedUntil, unmutePendingIntent);
            }
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MUTE_DURATION, builder.build());
        mutedCurrently = true;

        previousZenMode = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activateZenMode();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void activateZenMode() {
        ZenModeChange doNotDistrubChange = Preferences.getEnum(preferences, GlobalSettings.TIMED_MUTE_ZEN_CHANGE);

        if (doNotDistrubChange != ZenModeChange.NO_CHANGE) {
            int newZenMode;

            switch (doNotDistrubChange) {
                case ALARMS:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        newZenMode = NotificationListenerService.INTERRUPTION_FILTER_ALARMS;
                    } else {
                        newZenMode = NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
                    }
                    break;
                case PRIORITY:
                    newZenMode = NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
                    break;
                default:
                    newZenMode = NotificationListenerService.INTERRUPTION_FILTER_NONE;
            }

            previousZenMode = notificationService.getCurrentInterruptionFilter();
            try {
                notificationService.requestInterruptionFilter(newZenMode);
            } catch (SecurityException e) {
                Timber.w("Notification listener has been disabled before unmute.");
            }
        }
    }

    private void unmute() {
        System.out.println("Unmute! " + mutedCurrently);
        if (!mutedCurrently) {
            return;
        }

        mutedCurrently = false;
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_MUTE_DURATION);
        alarmManager.cancel(unmutePendingIntent);

        if (previousZenMode != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                notificationService.requestInterruptionFilter(previousZenMode);
            } catch (SecurityException e) {
                Timber.w("Notification listener has been disabled before unmute.");
            }
            previousZenMode = 0;
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(CommPaths.COMMAND_TIMED_MUTE)) {
            TimedMuteCommand timedMuteCommand = ParcelPacker.getParcelable(messageEvent.getData(), TimedMuteCommand.CREATOR);

            Wearable.MessageApi.sendMessage(googleApiClient, messageEvent.getSourceNodeId(), CommPaths.COMMAND_RECEIVAL_ACKNOWLEDGMENT, null);

            mute(timedMuteCommand);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.MessageApi.addListener(googleApiClient, this, Uri.parse("wear://*" + CommPaths.COMMAND_TIMED_MUTE), MessageApi.FILTER_LITERAL);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        GoogleApiAvailability.getInstance().showErrorNotification(context, connectionResult);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    private class UnmuteReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            unmute();
        }
    }
}
