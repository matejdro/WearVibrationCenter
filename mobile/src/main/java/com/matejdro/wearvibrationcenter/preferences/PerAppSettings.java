package com.matejdro.wearvibrationcenter.preferences;

import com.matejdro.wearutils.preferences.definition.EnumPreferenceDefinition;
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition;
import com.matejdro.wearutils.preferences.definition.SimplePreferenceDefinition;

import java.util.List;

public class PerAppSettings {
    public static final String VIRTUAL_APP_DEFAULT_SETTINGS = "com.matejdro.wearvibrationcenter.virtual.default";

    public static final EnumPreferenceDefinition<VibrationType> VIBRATION_TYPE = new EnumPreferenceDefinition<>("vibration_type", VibrationType.REGULAR);
    public static final PreferenceDefinition<long[]> VIBRATION_PATTERN = new SimplePreferenceDefinition<>("vibration_pattern", new long[] { 0, 500, 250, 500, 250, 500, 1000 });
    public static final PreferenceDefinition<Boolean> ONLY_VIBRATE_ORIGINAL_VIBRATING = new SimplePreferenceDefinition<>("only_vibrate_original_vibrating", true);
    public static final PreferenceDefinition<Boolean> RESPECT_ZEN_MODE = new SimplePreferenceDefinition<>("setting_respect_zen_mode", false);

    public static final PreferenceDefinition<Integer> MIN_VIBRATION_INTERVAL = new SimplePreferenceDefinition<>("min_vibration_interval", 5000);
    public static final PreferenceDefinition<Boolean> NO_UPDATE_VIBRATIONS = new SimplePreferenceDefinition<>("setting_no_update_vibration", false);
    public static final PreferenceDefinition<Boolean> NO_SUBSEQUENT_NOTIFICATION_VIBRATIONS = new SimplePreferenceDefinition<>("setting_no_subsequent_notification_vibration", false);

    public static final PreferenceDefinition<Boolean> RESPECT_THETAER_MODE = new SimplePreferenceDefinition<>("respect_theater_mode", true);
    public static final PreferenceDefinition<Boolean> RESPECT_CHARGING = new SimplePreferenceDefinition<>("respect_charging", true);

    public static final PreferenceDefinition<List<String>> EXCLUDING_REGEX = new SimplePreferenceDefinition<>("excluding_regex", null);
    public static final PreferenceDefinition<List<String>> INCLUDING_REGEX = new SimplePreferenceDefinition<>("including_regex", null);
    public static final PreferenceDefinition<List<String>> ALARM_REGEX = new SimplePreferenceDefinition<>("alarm_regex", null);

    public static final PreferenceDefinition<Integer> SNOOZE_DURATION = new SimplePreferenceDefinition<>("snooze_duration", 10 * 60);
}
