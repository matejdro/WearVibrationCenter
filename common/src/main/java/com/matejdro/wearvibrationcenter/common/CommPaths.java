package com.matejdro.wearvibrationcenter.common;

public interface CommPaths
{
    String COMMAND_VIBRATE = "/Command/Vibrate";
    String COMMAND_ALARM = "/Command/Alarm";
    String COMMAND_APP_MUTE = "/Command/AppMute";
    String COMMAND_TIMED_MUTE = "/Command/TimedMute";
    String COMMAND_SEND_LOGS = "/Command/SendLogs";

    String COMMAND_RECEIVAL_ACKNOWLEDGMENT = "/Ack";

    String CHANNEL_LOGS = "/Channel/Logs";

    String LIST_ACTIVE_APPS_NAMES = "/List/ActiveApps/Names";
    String LIST_ACTIVE_APPS_ICONS = "/List/ActiveApps/Icons";

    String ASSET_ICON = "Icon";
    String ASSET_BACKGROUND = "Background";

    String PREFERENCES_PREFIX = "/Preferences";
}
