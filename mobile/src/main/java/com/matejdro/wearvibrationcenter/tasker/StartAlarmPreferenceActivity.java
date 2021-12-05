package com.matejdro.wearvibrationcenter.tasker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition;
import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearutils.preferences.definition.SimplePreferenceDefinition;
import com.matejdro.wearutils.preferences.legacy.CustomStoragePreferenceFragment;
import com.matejdro.wearutils.tasker.TaskerPreferenceActivity;
import com.matejdro.wearvibrationcenter.R;

public class StartAlarmPreferenceActivity extends TaskerPreferenceActivity {
    public static final PreferenceDefinition<long[]> ALARM_PROPERTY_VIBRATION_PATTERN = new SimplePreferenceDefinition<>("vibration_pattern", new long[] { 0, 500, 250, 500, 250, 500, 1000 });
    public static final PreferenceDefinition<Uri> ALARM_PROPERTY_BACKGROUND_IMAGE = new SimplePreferenceDefinition<>("background_image", null);
    public static final PreferenceDefinition<Uri> ALARM_PROPERTY_ICON_IMAGE = new SimplePreferenceDefinition<>("icon_image", null);
    public static final PreferenceDefinition<String> ALARM_PROPERTY_DISPLAYED_TEXT = new SimplePreferenceDefinition<>("alarm_text", "");
    public static final PreferenceDefinition<Boolean> ALARM_PROPERTY_RESPECT_THETAER_MODE = new SimplePreferenceDefinition<>("respect_theater_mode", false);
    public static final PreferenceDefinition<Boolean> ALARM_PROPERTY_RESPECT_CHARGING = new SimplePreferenceDefinition<>("respect_charging", false);
    public static final PreferenceDefinition<Integer> ALARM_PROPERTY_SNOOZE_DURATION = new SimplePreferenceDefinition<>("snooze_duration", 10 * 60);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_single_fragment);

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, new PropertyListFragment())
                .commit();

        super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean onPreviousTaskerOptionsLoaded(Bundle taskerOptions) {
        if (taskerOptions.getInt("action", Integer.MAX_VALUE) != TaskerActions.ACTION_START_ALARM) {
            return false;
        }

        return super.onPreviousTaskerOptionsLoaded(taskerOptions);
    }

    @NonNull
    @Override
    protected String getDescription() {
        String alarmName = Preferences.getString(getCustomPreferences(), ALARM_PROPERTY_DISPLAYED_TEXT);
        return getString(R.string.tasker_start_alarm_with_name, alarmName);
    }

    @Override
    protected void onPreSave(Bundle settingsBundle, Intent taskerIntent) {
        settingsBundle.putInt("action", TaskerActions.ACTION_START_ALARM);
    }

    public static class PropertyListFragment extends CustomStoragePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.tasker_alarm);
        }
    }
}
