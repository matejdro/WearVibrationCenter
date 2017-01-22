package com.matejdro.wearvibrationcenter;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearutils.preferencesync.PreferenceReceiverService;

public class GlobalPreferenceReceiverService extends PreferenceReceiverService {
    public GlobalPreferenceReceiverService() {
        super(CommPaths.PREFERENCES_PREFIX);
    }

    @Override
    protected SharedPreferences.Editor getDestinationPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this).edit();
    }
}
