package com.matejdro.wearvibrationcenter.notification;

import android.app.Notification;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.util.ArrayMap;

import com.matejdro.wearutils.miscutils.BitmapUtils;
import com.matejdro.wearutils.miscutils.DeviceUtils;
import com.matejdro.wearutils.miscutils.TextUtils;
import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearvibrationcenter.common.AlarmCommand;
import com.matejdro.wearvibrationcenter.common.VibrationCommand;
import com.matejdro.wearvibrationcenter.preferences.PerAppSettings;
import com.matejdro.wearvibrationcenter.preferences.VibrationType;
import com.matejdro.wearvibrationcenter.watch.WatchCommander;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class NotificationProcessor {
    private final NotificationService service;
    private final Map<String, Long> lastVibrations = new ArrayMap<>();

    public NotificationProcessor(NotificationService service) {
        this.service = service;
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

        for (ProcessedNotification notification : pendingNotifications) {
            SharedPreferences appPreferences = notification.getAppPreferences();

            Timber.d("Processing %s", notification.getContentNotification().getPackageName());

            if (!filterNotification(notification)) {
                continue;
            }

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
                processAlarm(notification);
                return;
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
            forceScreenOn = respectCharger || Preferences.getBoolean(appPreferences, PerAppSettings.TURN_SCREEN_ON);

            lastVibrations.put(notification.getContentNotification().getPackageName(), System.currentTimeMillis());
        }

        pendingNotifications.clear();

        Timber.d("Vibrate: %s", Arrays.toString(longestPattern));
        if (longestPattern == null) {
            return;
        }

        final VibrationCommand vibrationCommand = new VibrationCommand(longestPattern,
                respectTheater,
                respectCharger,
                forceScreenOn);
        WatchCommander.sendVibrationCommand(service, vibrationCommand);
    }

    private void processAlarm(ProcessedNotification notification) {
        Timber.d("Alarm from %s", notification.getContentNotification().getPackageName());

        SharedPreferences appPreferences = notification.getAppPreferences();

        long[] vibrationPattern = Preferences.getLongArray(appPreferences, PerAppSettings.VIBRATION_PATTERN);
        boolean respectTheater = Preferences.getBoolean(appPreferences, PerAppSettings.RESPECT_THETAER_MODE);
        boolean respectCharger = Preferences.getBoolean(appPreferences, PerAppSettings.RESPECT_CHARGING);

        Bitmap icon = getNotificationIcon(notification.getContentNotification());
        Bitmap background = getNotificationBackgroundImage(notification.getContentNotification());
        background = BitmapUtils.shrinkPreservingRatio(background, 400, 400);

        int snoozeDuration = Preferences.getInt(appPreferences, PerAppSettings.SNOOZE_DURATION);

        AlarmCommand alarmCommand = new AlarmCommand(notification.getTitle(),
                vibrationPattern,
                BitmapUtils.serialize(background),
                BitmapUtils.serialize(icon),
                snoozeDuration * 1000, //duration is in milliseconds, setting is in seconds.
                respectTheater,
                respectCharger);

        WatchCommander.sendAlarmCommand(service, alarmCommand);
    }


    private boolean filterNotification(ProcessedNotification processedNotification) {
        if (!filterAndProcessWearGroup(processedNotification)) {
            Timber.d("Filter fail - wear group");
            return false;
        }


        SharedPreferences appPreferences = processedNotification.getAppPreferences();

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
                DeviceUtils.isScreenOn(service)) {
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

            StatusBarNotification[] currentNotifications = service.getActiveNotifications();
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

            StatusBarNotification[] currentNotifications = service.getActiveNotifications();
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

        NotificationListenerService.RankingMap rankingMap = service.getCurrentRanking();
        if (rankingMap == null) {
            return false;
        }

        NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
        return rankingMap.getRanking(statusBarNotification.getKey(), ranking) && !ranking.matchesInterruptionFilter();

    }

    public
    @Nullable
    Bitmap getNotificationIcon(StatusBarNotification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Icon icon = notification.getNotification().getSmallIcon();
            if (icon == null) {
                return null;
            }

            return BitmapUtils.getBitmap(service, icon);
        } else {
            @SuppressWarnings("deprecation")
            int iconId = notification.getNotification().icon;
            try {
                Resources sourceAppResources = service.getPackageManager().getResourcesForApplication(notification.getPackageName());
                return BitmapUtils.getBitmap(ResourcesCompat.getDrawable(sourceAppResources, iconId, null));
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public Bitmap getNotificationBackgroundImage(StatusBarNotification notification) {
        Bundle extras = NotificationCompat.getExtras(notification.getNotification());
        if (extras != null) {
            //Extract image from BigPictureStyle notification style
            Bitmap bitmap = BitmapUtils.getBitmap(service, extras.getParcelable(NotificationCompat.EXTRA_PICTURE));
            if (bitmap != null) {
                return bitmap;
            }

            //Extract image from Wearable extender background
            if (extras.containsKey("android.wearable.EXTENSIONS")) {
                Bundle wearableExtension = extras.getBundle("android.wearable.EXTENSIONS");
                bitmap = BitmapUtils.getBitmap(service, wearableExtension.getParcelable("background"));
                if (bitmap != null) {
                    return bitmap;
                }
            }

            //Extract image from Car extender large icon
            if (extras.containsKey("android.car.EXTENSIONS")) {
                Bundle carExtensions = extras.getBundle("android.car.EXTENSIONS");
                bitmap = BitmapUtils.getBitmap(service, carExtensions.getParcelable("large_icon"));
                if (bitmap != null) {
                    return bitmap;
                }
            }

            //Extract image from large icon on android notification
            bitmap = BitmapUtils.getBitmap(service, extras.getParcelable(NotificationCompat.EXTRA_LARGE_ICON_BIG));
            if (bitmap != null) {
                return bitmap;
            }

            bitmap = BitmapUtils.getBitmap(service, extras.getParcelable(NotificationCompat.EXTRA_LARGE_ICON));
            if (bitmap != null) {
                return bitmap;
            }
        }

        return null;
    }
}
