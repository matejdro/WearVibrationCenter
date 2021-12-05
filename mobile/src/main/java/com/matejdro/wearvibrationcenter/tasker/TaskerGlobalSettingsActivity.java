package com.matejdro.wearvibrationcenter.tasker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.matejdro.wearutils.tasker.TaskerPreferenceActivity;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.ui.GlobalSettingsFragment;

public class TaskerGlobalSettingsActivity extends TaskerPreferenceActivity {
    @NonNull
    @Override
    protected String getDescription() {
        return getString(R.string.tasker_global_settings);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_single_fragment);

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, new TaskerGlobalSettingsFragment())
                .commit();

        super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean onPreviousTaskerOptionsLoaded(Bundle taskerOptions) {
        if (taskerOptions.getInt("action", Integer.MAX_VALUE) != TaskerActions.ACTION_GLOBAL_SETTINGS) {
            return false;
        }

        return super.onPreviousTaskerOptionsLoaded(taskerOptions);
    }

    @Nullable
    @Override
    protected SharedPreferences getOriginalValues() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onPreSave(Bundle settingsBundle, Intent taskerIntent) {
        settingsBundle.putInt("action", TaskerActions.ACTION_GLOBAL_SETTINGS);
    }

    public static class TaskerGlobalSettingsFragment extends GlobalSettingsFragment
    {
        @Override
        protected boolean canTransmitSettingsAutomatically() {
            return false;
        }
    }
}
