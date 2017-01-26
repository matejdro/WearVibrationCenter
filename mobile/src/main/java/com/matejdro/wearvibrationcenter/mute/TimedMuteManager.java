package com.matejdro.wearvibrationcenter.mute;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.service.notification.NotificationListenerService;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearvibrationcenter.common.TimedMuteCommand;
import com.matejdro.wearutils.messages.ParcelPacker;
import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.notification.NotificationService;
import com.matejdro.wearvibrationcenter.preferences.ZenModeChange;
import com.matejdro.wearvibrationcenter.preferences.GlobalSettings;

public class TimedMuteManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {
    private static final int NOTIFICATION_ID_MUTE_DURATION = 0;
    private static final String ACTON_UNMUTE = "com.matejdro.wearvibrationcenter.action.UNMUTE";

    private final GoogleApiClient googleApiClient;
    private final BroadcastReceiver unmuteReceiver;
    private final NotificationService service;

    private final Handler handler = new Handler();

    private boolean mutedCurrently = false;
    private int previousZenMode = 0;

    public TimedMuteManager(NotificationService service) {
        this.service = service;

        googleApiClient = new GoogleApiClient.Builder(service)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();

        unmuteReceiver = new UnmuteReceiver();
        service.registerReceiver(unmuteReceiver, new IntentFilter(ACTON_UNMUTE));
    }

    public void onDestroy() {
        unmute();

        if (googleApiClient.isConnected()) {
            Wearable.MessageApi.removeListener(googleApiClient, this);
            googleApiClient.disconnect();
        }

        service.unregisterReceiver(unmuteReceiver);
        handler.removeCallbacksAndMessages(null);
    }

    public boolean isMutedCurrently() {
        return mutedCurrently;
    }

    private void mute(TimedMuteCommand timedMuteCommand) {
        long mutedUntil = -1;

        if (timedMuteCommand.getMuteDurationMinutes() > 0) {
            mutedUntil = System.currentTimeMillis() + timedMuteCommand.getMuteDurationMinutes() * 1000 * 60;
        }

        PendingIntent unmutePendingIntent = PendingIntent.getBroadcast(service, 0, new Intent(ACTON_UNMUTE), PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(Notification.PRIORITY_MIN)
                .setDeleteIntent(unmutePendingIntent)
                .setContentTitle(service.getString(R.string.vibrations_muted_notification_title))
                .setContentText(service.getString(R.string.vibrations_muted_notification_explanation));

        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();
        wearableExtender.addAction(new NotificationCompat.Action(R.drawable.ic_dismiss_mute, service.getString(R.string.cancel_mute), unmutePendingIntent));
        builder.extend(wearableExtender);

        if (mutedUntil > 0) {
            builder
                    .setWhen(mutedUntil)
                    .setUsesChronometer(true);

            // NotificationCompat is missing setChronometerCountDown(), lets do it manually.
            builder.getExtras().putBoolean("android.chronometerCountDown", true);

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    unmute();
                }
            }, mutedUntil - System.currentTimeMillis());
        }

        NotificationManagerCompat.from(service).notify(NOTIFICATION_ID_MUTE_DURATION, builder.build());
        mutedCurrently = true;

        previousZenMode = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activateZenMode();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void activateZenMode() {
        ZenModeChange doNotDistrubChange = Preferences.getEnum(service.getGlobalSettings(), GlobalSettings.TIMED_MUTE_ZEN_CHANGE);

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

            previousZenMode = service.getCurrentInterruptionFilter();
            service.requestInterruptionFilter(newZenMode);
        }
    }

    private void unmute() {
        if (!mutedCurrently) {
            return;
        }

        mutedCurrently = false;
        NotificationManagerCompat.from(service).cancel(NOTIFICATION_ID_MUTE_DURATION);
        handler.removeCallbacksAndMessages(null);

        if (previousZenMode != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            service.requestInterruptionFilter(previousZenMode);
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
        GoogleApiAvailability.getInstance().showErrorNotification(service, connectionResult);
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
