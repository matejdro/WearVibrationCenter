package com.matejdro.wearvibrationcenter.watch;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Parcelable;
import android.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearutils.preferencesync.PreferencePusher;
import com.matejdro.wearvibrationcenter.common.AlarmCommand;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearvibrationcenter.common.LiteAlarmCommand;
import com.matejdro.wearvibrationcenter.common.VibrationCommand;
import com.matejdro.wearutils.messages.MessagingUtils;
import com.matejdro.wearutils.messages.ParcelPacker;

public class WatchCommander extends IntentService {
    private static final String ACTION_VIBRATE_COMMAND = "VibrateCommand";
    private static final String ACTION_ALARM_COMMAND = "AlarmCommand";
    private static final String ACTION_TRANSMIT_GLOBAL_SETTINGS = "TransmitGlobalSettings";

    private static final String KEY_COMMAND_DATA = "CommandData";

    private GoogleApiClient googleApiClient;

    public WatchCommander() {
        super("WatchCommander");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        googleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        googleApiClient.disconnect();

        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!ensurePlayServicesConnection()) {
            return;
        }

        switch (intent.getAction()) {
            case ACTION_VIBRATE_COMMAND:
                vibrationCommand(intent);
                break;
            case ACTION_ALARM_COMMAND:
                alarmCommand(intent);
                break;
            case ACTION_TRANSMIT_GLOBAL_SETTINGS:
                transmitGlobalSettings();
                break;

        }
    }

    private void vibrationCommand(Intent intent) {
        Parcelable commandData = intent.getParcelableExtra(KEY_COMMAND_DATA);

        String watchId = MessagingUtils.getOtherNodeId(googleApiClient);
        Wearable.MessageApi.sendMessage(googleApiClient, watchId, CommPaths.COMMAND_VIBRATE, ParcelPacker.getData(commandData)).await();
    }

    private void alarmCommand(Intent intent) {
        AlarmCommand commandData = intent.getParcelableExtra(KEY_COMMAND_DATA);

        LiteAlarmCommand liteAlarmCommand = new LiteAlarmCommand(commandData);

        PutDataRequest putDataRequest = PutDataRequest.create(CommPaths.COMMAND_ALARM);
        putDataRequest.setData(ParcelPacker.getData(liteAlarmCommand));

        if (commandData.getIcon() != null) {
            putDataRequest.putAsset(CommPaths.ASSET_ICON, Asset.createFromBytes(commandData.getIcon()));
        }
        if (commandData.getBackgroundBitmap() != null) {
            putDataRequest.putAsset(CommPaths.ASSET_BACKGROUND, Asset.createFromBytes(commandData.getBackgroundBitmap()));
        }

        putDataRequest.setUrgent();

        Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).await();
    }

    private void transmitGlobalSettings() {
        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(this);
        PreferencePusher.pushPreferences(googleApiClient, globalSettings, CommPaths.PREFERENCES_PREFIX, false).await();
    }

    private boolean ensurePlayServicesConnection() {
        ConnectionResult result = googleApiClient.blockingConnect();
        if (!result.isSuccess()) {
            GoogleApiAvailability.getInstance().showErrorNotification(this, result);

            return false;
        }

        return true;
    }

    public static void sendVibrationCommand(Context context, VibrationCommand command) {
        Intent intent = new Intent(context, WatchCommander.class);
        intent.setAction(ACTION_VIBRATE_COMMAND);
        intent.putExtra(KEY_COMMAND_DATA, command);
        context.startService(intent);
    }

    public static void sendAlarmCommand(Context context, AlarmCommand command) {
        Intent intent = new Intent(context, WatchCommander.class);
        intent.setAction(ACTION_ALARM_COMMAND);
        intent.putExtra(KEY_COMMAND_DATA, command);
        context.startService(intent);
    }

    public static void transmitGlobalSettings(Context context) {
        Intent intent = new Intent(context, WatchCommander.class);
        intent.setAction(ACTION_TRANSMIT_GLOBAL_SETTINGS);
        context.startService(intent);
    }
}
