package com.matejdro.wearvibrationcenter.notification;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.collection.ArrayMap;
import androidx.core.app.NotificationCompat;

import com.matejdro.wearutils.miscutils.BitmapUtils;
import com.matejdro.wearutils.miscutils.DeviceUtils;
import com.matejdro.wearutils.miscutils.TextUtils;
import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.common.AlarmCommand;
import com.matejdro.wearvibrationcenter.common.VibrationCommand;
import com.matejdro.wearvibrationcenter.notificationprovider.NotificationBroadcaster;
import com.matejdro.wearvibrationcenter.preferences.PerAppSettings;
import com.matejdro.wearvibrationcenter.preferences.VibrationType;
import com.matejdro.wearvibrationcenter.watch.WatchCommander;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import timber.log.Timber;

public class NotificationProcessor {
    private final Context context;
    private final NotificationBroadcaster notificationBroadcaster;
    private final NotificationListenerService notificationService;
    private final Map<String, Long> lastVibrations = new ArrayMap<>();

    @Inject
    public NotificationProcessor(
            @ApplicationContext Context context,
            NotificationBroadcaster notificationBroadcaster,
            NotificationListenerService NotificationService
    ) {
        this.context = context;
        this.notificationBroadcaster = notificationBroadcaster;
        this.notificationService = NotificationService;
    }

    public void processPendingNotifications(List<ProcessedNotification> pendingNotifications) {
        Timber.d("Processing Pending %d", pendingNotifications.size());

        // Multiple notifications could be collected during processing wait.
        // Combine most "annoying" properties to make sure user does not miss any.
        long[] longestPattern = null;
        int longestPatternLength = -1;
        boolean respectTheater = true;
        boolean respectCharger = true;
        boolean forceScreenOn = false;

        ProcessedNotification lastFilteredNotification = null;

        List<ProcessedNotification> alarms = new ArrayList<>();

        for (ProcessedNotification notification : pendingNotifications) {
            Timber.d("Processing %s", notification.getContentNotification().getPackageName());

            if (!filterNotification(notification)) {
                continue;
            }
            SharedPreferences appPreferences = notification.getAppPreferences();

            VibrationType vibrationType = Preferences.getEnum(appPreferences, PerAppSettings.VIBRATION_TYPE);

            if (vibrationType != VibrationType.ALARM) {
                String combinedText = notification.getTitle() + " " + notification.getText();
                List<String> alarmRegexes = Preferences.getStringList(appPreferences, PerAppSettings.ALARM_REGEX);
                if (alarmRegexes != null && TextUtils.containsRegexes(combinedText, alarmRegexes)) {
                    Timber.d("Switch to alarm mode - alarm regex");
                    vibrationType = VibrationType.ALARM;
                }
            }

            if (vibrationType == VibrationType.ALARM) {
                alarms.add(notification);
            }

            Timber.d("Vibrating from %s", notification.getContentNotification().getPackageName());

            long[] vibrationPattern = Preferences.getLongArray(appPreferences, PerAppSettings.VIBRATION_PATTERN);


            int patternLength = getVibrationLength(vibrationPattern);
            if (patternLength > longestPatternLength) {
                longestPattern = vibrationPattern;
                longestPatternLength = patternLength;
            }

            respectTheater = respectTheater && Preferences.getBoolean(appPreferences, PerAppSettings.RESPECT_THETAER_MODE);
            respectCharger = respectCharger && Preferences.getBoolean(appPreferences, PerAppSettings.RESPECT_CHARGING);
            forceScreenOn = forceScreenOn || Preferences.getBoolean(appPreferences, PerAppSettings.TURN_SCREEN_ON);

            lastVibrations.put(notification.getContentNotification().getPackageName(), System.currentTimeMillis());

            lastFilteredNotification = notification;
        }

        pendingNotifications.clear();

        if (!alarms.isEmpty()) {
            Timber.d("Alarms %d %s", alarms.size(), alarms);
            ProcessedNotification firstAlarm = alarms.get(0);
            notificationBroadcaster.onNewNotification(firstAlarm);
            processAlarm(firstAlarm, alarms.size());
        }

        Timber.d("Vibrate: %s", Arrays.toString(longestPattern));
        if (longestPattern == null) {
            return;
        }

        if (lastFilteredNotification != null) {
            notificationBroadcaster.onNewNotification(lastFilteredNotification);
        }

        final VibrationCommand vibrationCommand = new VibrationCommand(longestPattern,
                respectTheater,
                respectCharger,
                forceScreenOn);
        WatchCommander.sendVibrationCommand(context, vibrationCommand);
    }

    private void processAlarm(ProcessedNotification notification, int numAlarms) {
        Timber.d("Alarm from %s", notification.getContentNotification().getPackageName());

        SharedPreferences appPreferences = notification.getAppPreferences();

        long[] vibrationPattern = Preferences.getLongArray(appPreferences, PerAppSettings.VIBRATION_PATTERN);
        boolean respectTheater = Preferences.getBoolean(appPreferences, PerAppSettings.RESPECT_THETAER_MODE);
        boolean respectCharger = Preferences.getBoolean(appPreferences, PerAppSettings.RESPECT_CHARGING);

        Bitmap icon = NotificationUtils.getNotificationIcon(context, notification.getContentNotification());
        Bitmap background = NotificationUtils.getNotificationBackgroundImage(context, notification.getContentNotification());
        background = BitmapUtils.shrinkPreservingRatio(background, 400, 400);

        int snoozeDuration = Preferences.getInt(appPreferences, PerAppSettings.SNOOZE_DURATION);

        String title;
        if (numAlarms > 1) {
            title = context.getString(R.string.multi_alarms, numAlarms);
        } else {
            title = notification.getTitle();
        }

        AlarmCommand alarmCommand = new AlarmCommand(title,
                vibrationPattern,
                BitmapUtils.serialize(background),
                BitmapUtils.serialize(icon),
                snoozeDuration * 1000, //duration is in milliseconds, setting is in seconds.
                respectTheater,
                respectCharger);

        WatchCommander.sendAlarmCommand(context, alarmCommand);
    }


    private boolean filterNotification(ProcessedNotification processedNotification) {
        if (!filterAndProcessWearGroup(processedNotification)) {
            Timber.d("Filter fail - wear group");
            return false;
        }

        SharedPreferences appPreferences = processedNotification.getAppPreferences();

        if (!Preferences.getBoolean(appPreferences, PerAppSettings.ALLOW_PERSISTENT) &&
                !processedNotification.getMetadataNotification().isClearable()) {
            Timber.d("Notification filtered: persistent ");
            return false;
        }

        if (!Preferences.getBoolean(appPreferences, PerAppSettings.IGNORE_LOCAL_ONLY) &&
                NotificationCompat.getLocalOnly(processedNotification.getMetadataNotification().getNotification())) {
            Timber.d("Filter fail - local only");
            return false;

        }

        if (Preferences.getBoolean(appPreferences, PerAppSettings.ONLY_VIBRATE_ORIGINAL_VIBRATING)) {
            Notification metadataNotification = processedNotification.getMetadataNotification().getNotification();
            if (isPassiveNotification(metadataNotification)) {
                Timber.d("Filter fail - passive notification");
                return false;
            }
        }

        if (Preferences.getBoolean(appPreferences, PerAppSettings.RESPECT_ZEN_MODE)) {
            if (isNotificationFilteredByDoNotInterrupt(processedNotification.getMetadataNotification())) {
                Timber.d("Filter fail - zen mode");
                return false;
            }
        }

        if (Preferences.getBoolean(appPreferences, PerAppSettings.NO_VIBRATIONS_SCREEN_ON) &&
                DeviceUtils.isScreenOn(context)) {
            Timber.d("Filter fail - screen on");
            return false;
        }

        int minVibrationInterval = Preferences.getInt(appPreferences, PerAppSettings.MIN_VIBRATION_INTERVAL);
        if (minVibrationInterval > 0) {
            Long lastVibration = lastVibrations.get(processedNotification.getContentNotification().getPackageName());
            if (lastVibration != null && lastVibration + minVibrationInterval > System.currentTimeMillis()) {
                Timber.d("Filter fail - min vibration interval");
                return false;
            }
        }

        if (processedNotification.isUpdateNotification() &&
                Preferences.getBoolean(appPreferences, PerAppSettings.NO_UPDATE_VIBRATIONS)) {
            Timber.d("Filter fail - update");
            return false;
        }

        if (processedNotification.isSubsequentNotification() &&
                Preferences.getBoolean(appPreferences, PerAppSettings.NO_SUBSEQUENT_NOTIFICATION_VIBRATIONS)) {
            Timber.d("Filter fail - existing notifications");
            return false;
        }

        String combinedText = processedNotification.getTitle() + " " + processedNotification.getText();
        List<String> excludingRegexes = Preferences.getStringList(appPreferences, PerAppSettings.EXCLUDING_REGEX);
        if (excludingRegexes != null && TextUtils.containsRegexes(combinedText, excludingRegexes)) {
            Timber.d("Filter fail - excluding regex");
            return false;
        }

        List<String> includingRegexes = Preferences.getStringList(appPreferences, PerAppSettings.INCLUDING_REGEX);
        if (includingRegexes != null && includingRegexes.size() > 0 && !TextUtils.containsRegexes(combinedText, includingRegexes)) {
            Timber.d("Filter fail - including regex");
            return false;
        }

        return true;
    }

    private boolean isPassiveNotification(Notification notification) {
        // If notification specified either default vibration or default sound, mark it as non-passive
        if ((notification.defaults & (Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND)) != 0) {
            return false;
        }

        return getVibrationLength(notification.vibrate) == 0 && notification.sound == null;

    }

    private boolean filterAndProcessWearGroup(ProcessedNotification notification) {
        String group = NotificationCompat.getGroup(notification.getContentNotification().getNotification());
        if (android.text.TextUtils.isEmpty(group)) {
            return true;
        }

        String appPackage = notification.getContentNotification().getPackageName();

        if (NotificationCompat.isGroupSummary(notification.getContentNotification().getNotification())) {
            // If notification is summary and there are content notifications, it should go through

            StatusBarNotification[] currentNotifications = notificationService.getActiveNotifications();
            for (StatusBarNotification otherNotification : currentNotifications) {
                if (otherNotification.getPackageName().equals(appPackage) &&
                        group.equals(NotificationCompat.getGroup(otherNotification.getNotification())) &&
                        !NotificationCompat.isGroupSummary(otherNotification.getNotification())) {
                    // Any present group notifications negate the summary
                    return false;
                }
            }
        } else {
            // If notification is not summary, then we attempt to get metadata (vibration etc.) from the summary

            StatusBarNotification[] currentNotifications = notificationService.getActiveNotifications();
            for (StatusBarNotification otherNotification : currentNotifications) {
                if (otherNotification.getPackageName().equals(appPackage) &&
                        group.equals(NotificationCompat.getGroup(otherNotification.getNotification())) &&
                        NotificationCompat.isGroupSummary(otherNotification.getNotification())) {
                    notification.setMetadataNotification(otherNotification);
                    break;
                }
            }
        }


        return true;
    }

    private int getVibrationLength(long[] pattern) {
        if (pattern == null || pattern.length < 2) {
            return 0;
        }

        int length = 0;

        for (int i = 1; i < pattern.length; i += 2) {
            length += pattern[i];
        }

        return length;
    }

    public boolean isNotificationFilteredByDoNotInterrupt(StatusBarNotification statusBarNotification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true;
        }

        NotificationListenerService.RankingMap rankingMap = notificationService.getCurrentRanking();
        if (rankingMap == null) {
            return false;
        }

        NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
        return rankingMap.getRanking(statusBarNotification.getKey(), ranking) && !ranking.matchesInterruptionFilter();

    }

}
