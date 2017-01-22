package com.matejdro.wearvibrationcenter.preferences;

import com.matejdro.wearutils.preferences.definition.EnumPreferenceDefinition;
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition;
import com.matejdro.wearutils.preferences.definition.SimplePreferenceDefinition;

import java.util.Arrays;
import java.util.List;

public class GlobalSettings {
    public static final PreferenceDefinition<Boolean> ENABLE_VIBRATIONS = new SimplePreferenceDefinition<>("enable_vibrations", true);
    public static final PreferenceDefinition<Integer> ALARM_TIMEOUT = new SimplePreferenceDefinition<>("alarm_timeout", 60);
    public static final PreferenceDefinition<List<String>> MUTE_INTERVALS = new SimplePreferenceDefinition<>("mute_intervals", Arrays.asList("10", "20", "30", "60", "120"));
    public static final EnumPreferenceDefinition<ZenModeChange> TIMED_MUTE_ZEN_CHANGE = new EnumPreferenceDefinition<>("timed_mute_zen", ZenModeChange.ALARMS);
    public static final PreferenceDefinition<Integer> PROCESSING_DELAY = new SimplePreferenceDefinition<>("processing_delay", 2000);


}
