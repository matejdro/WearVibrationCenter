package com.matejdro.wearvibrationcenter.tasker;

import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.matejdro.wearutils.tasker.TaskerPreferenceActivity;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.preferences.PerAppSettings;
import com.matejdro.wearvibrationcenter.preferences.PerAppSharedPreferences;
import com.matejdro.wearvibrationcenter.ui.AppPickerFragment;
import com.matejdro.wearvibrationcenter.ui.PerAppSettingsFragment;

public class TaskerAppSettingsActivity extends TaskerPreferenceActivity implements AppPickerFragment.AppPickerCallback {
    public static final String EXTRA_SYSTEM_APPS = "SystemApps";
    public static final String EXTRA_SHOW_APP = "ShowApp";

    private boolean settingsFragmentDisplayed = false;

    private SharedPreferences originalPreferences;

    private String appPackage;
    private String appLabel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_single_fragment);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean onPreviousTaskerOptionsLoaded(Bundle taskerOptions) {
        appPackage = taskerOptions.getString("PickedApp");
        if (appPackage == null) {
            return false;
        }

        loadOriginalPreferences(appPackage);

        if (!super.onPreviousTaskerOptionsLoaded(taskerOptions)) {
            return false;
        }

        showAppFragment(appPackage, getAppNameFromPackage(appPackage));

        return true;
    }

    @Override
    protected void onFreshTaskerSetup() {
        Intent argumentsIntent = getIntent();

        String forcedAppPackage = argumentsIntent.getStringExtra(EXTRA_SHOW_APP);
        if (forcedAppPackage != null) {
            loadOriginalPreferences(forcedAppPackage);
            super.onFreshTaskerSetup();

            showAppFragment(forcedAppPackage, getAppNameFromPackage(forcedAppPackage));
        } else {
            boolean systemApps = argumentsIntent.getBooleanExtra(EXTRA_SYSTEM_APPS, false);
            showAppPickerFragment(systemApps);
            super.onFreshTaskerSetup();

        }

    }

    private String getAppNameFromPackage(String appPackage) {
        if (PerAppSettings.VIRTUAL_APP_DEFAULT_SETTINGS.equals(appPackage)) {
            return getString(R.string.default_app_settings);
        }

        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(appPackage, 0);
            return getPackageManager().getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return appPackage;
        }
    }

    @NonNull
    @Override
    protected String getDescription() {
        if (PerAppSettings.VIRTUAL_APP_DEFAULT_SETTINGS.equals(appPackage)) {
            return appLabel;
        } else {
            return getString(R.string.tasker_change_specific_app, appLabel);
        }
    }

    @Override
    public void onAppPicked(String appPackage, String appLabel) {
        loadOriginalPreferences(appPackage);
        initPreferences();
        showAppFragment(appPackage, appLabel);
    }

    private void loadOriginalPreferences(String appPackage) {
        originalPreferences = PerAppSharedPreferences.getPerAppSharedPreferences(this, appPackage);
    }

    private void showAppFragment(String appPackage, String appLabel) {
        this.appPackage = appPackage;
        this.appLabel = appLabel;

        setTitle(appLabel);

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, PerAppSettingsFragment.newInstance(appPackage, appLabel))
                .commit();

        settingsFragmentDisplayed = true;
    }

    private void showAppPickerFragment(boolean systemApps) {
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, AppPickerFragment.newInstance(systemApps))
                .commit();

        settingsFragmentDisplayed = false;
    }

    @Nullable
    @Override
    protected SharedPreferences getOriginalValues() {
        return originalPreferences;
    }

    @Override
    protected void onPreSave(Bundle settingsBundle, Intent taskerIntent) {
        settingsBundle.putString("PickedApp", appPackage);
        settingsBundle.putInt("action", TaskerActions.ACTION_APP_SETTINGS);
    }

    @Override
    protected void save() {
        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.content_frame);

        if (currentFragment != null && currentFragment instanceof PerAppSettingsFragment) {
            super.save();
        }
    }
}
