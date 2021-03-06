package com.matejdro.wearvibrationcenter.notification;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearvibrationcenter.mute.AppMuteManager;
import com.matejdro.wearvibrationcenter.mute.TimedMuteManager;
import com.matejdro.wearvibrationcenter.notificationprovider.NotificationBroadcaster;
import com.matejdro.wearvibrationcenter.preferences.GlobalSettings;
import com.matejdro.wearvibrationcenter.preferences.PerAppSettings;
import com.matejdro.wearvibrationcenter.preferences.PerAppSharedPreferences;
import com.matejdro.wearvibrationcenter.preferences.VibrationType;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;

import timber.log.Timber;

public class NotificationService extends NotificationListenerService {
    public static final int MESSAGE_PROCESS_PENDING = 0;
    public static final int MESSAGE_ADD_NOTIFICATION = 1;
    public static final int MESSAGE_REMOVE_NOTIFICATION = 2;
    public static final int MESSAGE_UPDATE_ACTIVE_LIST = 3;

    public static boolean active;
    private final LinkedList<ProcessedNotification> pendingNotifications = new LinkedList<>();
    private Handler handler;
    /**
     * To determine whether notification is new or just an update of old notification,
     * we need a list of previous notifications before new one appeared
     */
    private StatusBarNotification[] previousList = new StatusBarNotification[0];
    private NotificationProcessor processor;
    private TimedMuteManager timedMuteManager;
    private AppMuteManager appMuteManager;
    private NotificationBroadcaster notificationBroadcaster;

    private SharedPreferences globalSettings;

    @Override
    public void onDestroy() {
        active = false;

        handler.removeCallbacksAndMessages(null);

        timedMuteManager.onDestroy();
        appMuteManager.onDestroy();
        notificationBroadcaster.onDestroy();
        super.onDestroy();
        Timber.d("Notification Listener stopped...");
    }

    @Override
    public void onCreate() {
        Timber.d("Creating Notification Listener...");

        active = true;

        super.onCreate();

        handler = new NotificationRefreshHandler(this);
        globalSettings = PreferenceManager.getDefaultSharedPreferences(this);

        processor = new NotificationProcessor(this);
        timedMuteManager = new TimedMuteManager(this);
        appMuteManager = new AppMuteManager(this);
        notificationBroadcaster = new NotificationBroadcaster(this);

        scheduleActiveListUpdate();
    }

    @Override
    public void onNotificationPosted(final StatusBarNotification sbn) {
        Timber.d("Got new jellybean notification %s", sbn.getPackageName());

        scheduleActiveListUpdate();

        if (timedMuteManager.isMutedCurrently()) {
            Timber.d("Notification filtered: timed mute ");
            return;
        }

        if (!Preferences.getBoolean(globalSettings, GlobalSettings.ENABLE_VIBRATIONS)) {
            Timber.d("Notification filtered: global toggle disable");
            return;
        }

        SharedPreferences notificationPreferences = PerAppSharedPreferences.getPerAppSharedPreferences(this, sbn.getPackageName());
        ProcessedNotification processedNotification = new ProcessedNotification(sbn, notificationPreferences);
        fillMetadata(processedNotification);

        if (appMuteManager.isNotificationMuted(processedNotification)) {
            Timber.d("Notification filtered: app mute");
            return;
        }

        Message message = Message.obtain();
        message.what = MESSAGE_ADD_NOTIFICATION;
        message.obj = processedNotification;
        handler.sendMessage(message);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification removedNotification) {
        Timber.d("Got jellybean dismiss %s %d", removedNotification.getPackageName(), removedNotification.getId());

        Message message = Message.obtain();
        message.what = MESSAGE_REMOVE_NOTIFICATION;
        message.obj = removedNotification;
        handler.sendMessage(message);

        scheduleActiveListUpdate();
    }

    @Override
    public StatusBarNotification[] getActiveNotifications() {
        try {
            StatusBarNotification[] notifications = super.getActiveNotifications();
            if (notifications == null) {
                return new StatusBarNotification[0];
            } else {
                return notifications;
            }
        } catch (Exception ignored) {
            // Sometimes notification service will throw internal exception
            Timber.w("Notification service threw error!");
            return new StatusBarNotification[0];
        }
    }


    public SharedPreferences getGlobalSettings() {
        return globalSettings;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void requestInterruptionFilterSafe(int interruptionFilter) {
        try {
            requestInterruptionFilter(interruptionFilter);
        } catch (SecurityException e) {
            Timber.w("Notification listener has been disabled before unmute.");
        }
    }

    public void onNotificationVibrated(ProcessedNotification notification) {
        notificationBroadcaster.onNewNotification(notification);
    }

    private void fillMetadata(ProcessedNotification notification)
    {
        for (StatusBarNotification other : previousList) {
            if (notification.containsSameNotification(other)) {
                notification.setUpdateNotification(true);
                break;
            }
        }

        if (!notification.isUpdateNotification()) {
            for (StatusBarNotification other : previousList) {
                if (notification.getContentNotification().getPackageName().equals(other.getPackageName())) {
                    notification.setSubsequentNotification(true);
                    break;
                }
            }
        }

        new NotificationTextParser(this).parse(notification, notification.getContentNotification());
    }

    private void addNotificationSync(ProcessedNotification notification)
    {
        if (Preferences.getEnum(notification.getAppPreferences(), PerAppSettings.VIBRATION_TYPE) == VibrationType.NONE) {
            Timber.d("Notification filtered: vibration type");
            return;
        }

        pendingNotifications.push(notification);

        // at least 250 millisecond delay between notification posting and processing
        // is enforced to catch all notifications in the wear group
        int procesingDelay = Preferences.getInt(globalSettings, GlobalSettings.PROCESSING_DELAY);
        procesingDelay = Math.max(250, procesingDelay);

        handler.removeMessages(MESSAGE_PROCESS_PENDING);
        handler.sendMessageDelayed(Message.obtain(handler, MESSAGE_PROCESS_PENDING), procesingDelay);
    }

    private void removeNotificationSync(StatusBarNotification removedSbn)
    {
        for (Iterator<ProcessedNotification> i = pendingNotifications.iterator(); i.hasNext();) {
            if (i.next().containsSameNotification(removedSbn)) {
                i.remove();
            }
        }
    }

    private void processPendingSync()
    {
        processor.processPendingNotifications(pendingNotifications);
        pendingNotifications.clear();
    }

    private void scheduleActiveListUpdate()
    {
        handler.removeMessages(MESSAGE_UPDATE_ACTIVE_LIST);
        handler.sendEmptyMessageDelayed(MESSAGE_UPDATE_ACTIVE_LIST, 250);
    }

    private void updateActiveList()
    {
        previousList = getActiveNotifications();
    }

    private static class NotificationRefreshHandler extends Handler {
        private final WeakReference<NotificationService> service;

        public NotificationRefreshHandler(NotificationService service) {
            this.service = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            NotificationService listener = service.get();
            if (listener == null)
                return;

            switch (msg.what) {
                case MESSAGE_PROCESS_PENDING:
                    listener.processPendingSync();
                    break;
                case MESSAGE_ADD_NOTIFICATION:
                    listener.addNotificationSync((ProcessedNotification) msg.obj);
                    break;
                case MESSAGE_REMOVE_NOTIFICATION:
                    listener.removeNotificationSync((StatusBarNotification) msg.obj);
                    break;
                case MESSAGE_UPDATE_ACTIVE_LIST:
                    listener.updateActiveList();
                    break;
            }
        }
    }
}
