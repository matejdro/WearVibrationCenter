package com.matejdro.wearvibrationcenter.preferences;

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition;
import com.matejdro.wearutils.preferences.definition.SimplePreferenceDefinition;

public class GlobalWatchPreferences {
    public static final PreferenceDefinition<Boolean> DO_NOT_ASK_APP_MUTE_CONFIRM = new SimplePreferenceDefinition<>("do_not_ask_app_mute_confirm", false);
}
