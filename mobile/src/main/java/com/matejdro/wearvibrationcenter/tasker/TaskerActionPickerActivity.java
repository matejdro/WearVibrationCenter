package com.matejdro.wearvibrationcenter.tasker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.matejdro.wearutils.tasker.LocaleConstants;
import com.matejdro.wearutils.tasker.TaskerSetupActivity;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.preferences.PerAppSettings;

public class TaskerActionPickerActivity extends TaskerSetupActivity {
    private static final int TASKER_ACTION_REQUEST = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasker_actions);
    }

    @Override
    protected boolean onPreviousTaskerOptionsLoaded(Bundle taskerOptions) {
        Intent nextScreenIntent = null;

        int action = taskerOptions.getInt("action", Integer.MAX_VALUE);

        switch (action) {
            case TaskerActions.ACTION_START_ALARM:
                nextScreenIntent = new Intent(this, StartAlarmPreferenceActivity.class);
                break;
            case TaskerActions.ACTION_GLOBAL_SETTINGS:
                nextScreenIntent = new Intent(this, TaskerGlobalSettingsActivity.class);
                break;
            case TaskerActions.ACTION_APP_SETTINGS:
                nextScreenIntent = new Intent(this, TaskerAppSettingsActivity.class);
                break;
        }

        if (nextScreenIntent == null) {
            return false;
        }

        nextScreenIntent.putExtra(LocaleConstants.EXTRA_BUNDLE, taskerOptions);
        startActivityForResult(nextScreenIntent, TASKER_ACTION_REQUEST);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == TASKER_ACTION_REQUEST && resultCode == RESULT_OK)
        {
            setResult(RESULT_OK, data);
            finish();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed()
    {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    public void startAlarm(View view) {
        startActivityForResult(new Intent(this, StartAlarmPreferenceActivity.class), TASKER_ACTION_REQUEST);
    }

    public void changeGlobalSettings(View view) {
        startActivityForResult(new Intent(this, TaskerGlobalSettingsActivity.class), TASKER_ACTION_REQUEST);
    }

    public void changeDefaultAppSettings(View view) {
        Intent appSettingsActivityIntent = new Intent(this, TaskerAppSettingsActivity.class);
        appSettingsActivityIntent.putExtra(TaskerAppSettingsActivity.EXTRA_SHOW_APP, PerAppSettings.VIRTUAL_APP_DEFAULT_SETTINGS);

        startActivityForResult(appSettingsActivityIntent, TASKER_ACTION_REQUEST);
    }

    public void changeUserAppSettings(View view) {
        Intent appSettingsActivityIntent = new Intent(this, TaskerAppSettingsActivity.class);
        appSettingsActivityIntent.putExtra(TaskerAppSettingsActivity.EXTRA_SYSTEM_APPS, false);

        startActivityForResult(appSettingsActivityIntent, TASKER_ACTION_REQUEST);
    }

    public void changeSystemAppSettings(View view) {
        Intent appSettingsActivityIntent = new Intent(this, TaskerAppSettingsActivity.class);
        appSettingsActivityIntent.putExtra(TaskerAppSettingsActivity.EXTRA_SYSTEM_APPS, true);

        startActivityForResult(appSettingsActivityIntent, TASKER_ACTION_REQUEST);
    }
}
