package com.matejdro.wearvibrationcenter;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Vibrator;
import android.support.annotation.Nullable;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemAsset;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.matejdro.wearutils.WatchUtils;
import com.matejdro.wearutils.logging.LogTransmitter;
import com.matejdro.wearutils.messages.ParcelPacker;
import com.matejdro.wearvibrationcenter.common.AlarmCommand;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearvibrationcenter.common.InterruptionCommand;
import com.matejdro.wearvibrationcenter.common.LiteAlarmCommand;
import com.matejdro.wearvibrationcenter.common.VibrationCommand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import timber.log.Timber;

public class PhoneCommandListener extends WearableListenerService {
    @Nullable
    private static byte[] readFully(InputStream in) {
        if (in == null) {
            return null;
        }

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        int bytesRead;
        byte[] buffer = new byte[1024];

        try {
            while ((bytesRead = in.read(buffer, 0, buffer.length)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException ignored) {
            return null;
        }

        return outStream.toByteArray();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (CommPaths.COMMAND_VIBRATE.equals(messageEvent.getPath())) {
            VibrationCommand vibrationCommand = ParcelPacker.getParcelable(messageEvent.getData(), VibrationCommand.CREATOR);
            vibrate(vibrationCommand);
        } else if (CommPaths.COMMAND_SEND_LOGS.equals(messageEvent.getPath())) {
            Intent logSendingIntent = new Intent(this, LogTransmitter.class);
            logSendingIntent.putExtra(LogTransmitter.EXTRA_TARGET_NODE_ID, messageEvent.getSourceNodeId());
            logSendingIntent.putExtra(LogTransmitter.EXTRA_TARGET_PATH, CommPaths.CHANNEL_LOGS);
            startService(logSendingIntent);
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        GoogleApiClient googleApiClient = null;

        for (DataEvent event : dataEventBuffer) {
            if (event.getType() != DataEvent.TYPE_CHANGED) {
                continue;
            }

            DataItem dataItem = event.getDataItem();

            if (CommPaths.COMMAND_ALARM.equals(dataItem.getUri().getPath())) {
                LiteAlarmCommand liteAlarmCommand = ParcelPacker.getParcelable(dataItem.getData(), LiteAlarmCommand.CREATOR);

                if (googleApiClient == null) {
                    googleApiClient = new GoogleApiClient.Builder(this)
                            .addApi(Wearable.API)
                            .build();

                    googleApiClient.blockingConnect();
                }

                byte[] iconData = getByteArrayAsset(dataItem.getAssets().get(CommPaths.ASSET_ICON), googleApiClient);
                byte[] backgroundData = getByteArrayAsset(dataItem.getAssets().get(CommPaths.ASSET_BACKGROUND), googleApiClient);

                AlarmCommand alarmCommand = new AlarmCommand(liteAlarmCommand, backgroundData, iconData);
                alarm(alarmCommand);

                Wearable.DataApi.deleteDataItems(googleApiClient, dataItem.getUri()).await();
            }
        }

        if (googleApiClient != null) {
            googleApiClient.disconnect();
        }
    }

    private void vibrate(VibrationCommand vibrationCommand) {
        if (!filterCommand(vibrationCommand)) {
            return;
        }

        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate(vibrationCommand.getPattern(), -1);
    }

    private void alarm(AlarmCommand alarmCommand) {
        if (!filterCommand(alarmCommand)) {
            return;
        }

        Intent alarmActivityIntent = new Intent(this, AlarmActivity.class);
        alarmActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        alarmActivityIntent.putExtra(AlarmActivity.EXTRA_ALARM_COMMAND_BYTES, ParcelPacker.getData(alarmCommand));
        startActivity(alarmActivityIntent);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean filterCommand(InterruptionCommand command) {
        if (command.shouldNotVibrateOnCharger()) {
            Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            boolean pluggedIn = batteryIntent != null && batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            if (pluggedIn) {
                Timber.d("Filter - Charger!");
                return false;
            }
        }

        if (command.shouldNotVibrateInTheater() &&
                WatchUtils.isWatchInTheaterMode(this)) {
            Timber.d("Filter - Theater mode!");
            return false;
        }

        return true;
    }

    @Nullable
    private byte[] getByteArrayAsset(@Nullable DataItemAsset asset, GoogleApiClient connectedApiClient) {
        if (asset == null) {
            return null;
        }

        InputStream inputStream = Wearable.DataApi.getFdForAsset(connectedApiClient, asset).await().getInputStream();
        byte[] data = readFully(inputStream);
        if (data != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }

        return data;
    }

}
