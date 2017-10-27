package com.matejdro.wearvibrationcenter.ui;

import android.content.SharedPreferences;
import android.os.Bundle;

import com.matejdro.wearutils.preferences.PreferenceSource;
import com.matejdro.wearutils.preferences.legacy.CustomStoragePreferenceFragment;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.preferences.PerAppSharedPreferences;

public class PerAppSettingsFragment extends CustomStoragePreferenceFragment implements TitleUtils.TitledFragment
{
    private static final String ARGUMENT_PACKAGE = "Package";
    private static final String ARGUMENT_LABEL = "Label";

    private String appLabel;

    public static PerAppSettingsFragment newInstance(String appPackage, String appLabel)
    {
        Bundle arguments = new Bundle();
        arguments.putString(ARGUMENT_PACKAGE, appPackage);
        arguments.putString(ARGUMENT_LABEL, appLabel);

        PerAppSettingsFragment perAppSettingsFragment = new PerAppSettingsFragment();
        perAppSettingsFragment.setArguments(arguments);

        return perAppSettingsFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        appLabel = arguments.getString(ARGUMENT_LABEL);
        String appPackage = arguments.getString(ARGUMENT_PACKAGE);

        // If activity does not provide override preferences, define our own
        if (!(getActivity() instanceof PreferenceSource)) {
            injectSharedPreferences(getPreferences(appPackage));
        }

        addPreferencesFromResource(R.xml.perapp_settings);
    }

    protected SharedPreferences getPreferences(String appPackage)
    {
        return PerAppSharedPreferences.getPerAppSharedPreferences(getActivity(), appPackage);
    }

    @Override
    public String getTitle() {
        return appLabel;
    }
}
