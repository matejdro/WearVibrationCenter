package com.matejdro.wearvibrationcenter.tasker;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.matejdro.wearutils.miscutils.BitmapUtils;
import com.matejdro.wearutils.preferences.BundleSharedPreferences;
import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearutils.tasker.LocaleConstants;
import com.matejdro.wearvibrationcenter.common.AlarmCommand;
import com.matejdro.wearvibrationcenter.preferences.PerAppSharedPreferences;
import com.matejdro.wearvibrationcenter.watch.WatchCommander;

import timber.log.Timber;


public class TaskerActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;

        Bundle bundle = intent.getBundleExtra(LocaleConstants.EXTRA_BUNDLE);
        if (bundle == null)
            return;

        int action = bundle.getInt("action", Integer.MAX_VALUE);

        switch (action) {
            case TaskerActions.ACTION_START_ALARM:
                startAlarm(context, bundle);
                break;
            case TaskerActions.ACTION_GLOBAL_SETTINGS:
                changeGlobalSettings(context, bundle);
                break;
            case TaskerActions.ACTION_APP_SETTINGS:
                changeAppSettings(context, bundle);
                break;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void startAlarm(final Context context, final Bundle data) {
        final PendingResult result = goAsync();

        new AlarmTask(context, data, result).execute((Void) null);
    }

    private void changeGlobalSettings(Context context, Bundle data) {
        SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = defaultPreferences.edit();
        BundleSharedPreferences.applyPreferencesFromBundle(editor, data);
        editor.apply();

        WatchCommander.transmitGlobalSettings(context);
    }

    private void changeAppSettings(Context context, Bundle data) {
        String appPackage = data.getString("PickedApp");
        if (appPackage == null) {
            Timber.e("Received tasker AppSettings bundle without app package.");
            return;
        }

        SharedPreferences appPreferences = PerAppSharedPreferences.getPerAppSharedPreferences(context, appPackage);
        SharedPreferences.Editor editor = appPreferences.edit();
        BundleSharedPreferences.applyPreferencesFromBundle(editor, data);
        editor.apply();
    }

    private static class AlarmTask extends AsyncTask<Void, Void, Void> {
        private int resultError = 0;
        @SuppressLint("StaticFieldLeak")
        private Context context;
        private Bundle data;
        private PendingResult result;

        public AlarmTask(Context context, Bundle data, PendingResult result) {
            this.context = context;
            this.data = data;
            this.result = result;
        }

        protected Void doInBackground(Void... voids) {
            SharedPreferences alarmProperties = new BundleSharedPreferences(null, data);

            String text = Preferences.getString(alarmProperties, StartAlarmPreferenceActivity.ALARM_PROPERTY_DISPLAYED_TEXT);
            Uri backgroundImageUri = Preferences.getUri(alarmProperties, StartAlarmPreferenceActivity.ALARM_PROPERTY_BACKGROUND_IMAGE);
            Uri iconUri = Preferences.getUri(alarmProperties, StartAlarmPreferenceActivity.ALARM_PROPERTY_ICON_IMAGE);
            long[] vibrationPattern = Preferences.getLongArray(alarmProperties, StartAlarmPreferenceActivity.ALARM_PROPERTY_VIBRATION_PATTERN);
            int snoozeDuration = Preferences.getInt(alarmProperties, StartAlarmPreferenceActivity.ALARM_PROPERTY_SNOOZE_DURATION);
            boolean respectTheaterMode = Preferences.getBoolean(alarmProperties, StartAlarmPreferenceActivity.ALARM_PROPERTY_RESPECT_THETAER_MODE);
            boolean respectCharging = Preferences.getBoolean(alarmProperties, StartAlarmPreferenceActivity.ALARM_PROPERTY_RESPECT_CHARGING);

            Bitmap iconImage = null;
            Bitmap backgroundImage = null;

            try {
                iconImage = BitmapUtils.getBitmap(BitmapUtils.getDrawableFromUri(context, iconUri));
                iconImage = BitmapUtils.shrinkPreservingRatio(iconImage, 64, 64);

                backgroundImage = BitmapUtils.getBitmap(BitmapUtils.getDrawableFromUri(context, backgroundImageUri));
                backgroundImage = BitmapUtils.shrinkPreservingRatio(backgroundImage, 400, 400);
            } catch (SecurityException e) {
                resultError = com.matejdro.wearutils.R.string.no_storage_permission;
            }

            AlarmCommand alarmCommand = new AlarmCommand(text,
                    vibrationPattern,
                    BitmapUtils.serialize(backgroundImage),
                    BitmapUtils.serialize(iconImage),
                    snoozeDuration,
                    respectTheaterMode,
                    respectCharging);

            WatchCommander.sendAlarmCommand(context, alarmCommand);
            result.finish();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (resultError != 0) {
                Toast.makeText(context, resultError, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
