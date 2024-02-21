package com.matejdro.wearvibrationcenter.notification

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearvibrationcenter.di.NotificationServiceComponent
import com.matejdro.wearvibrationcenter.di.NotificationServiceEntryPoint
import com.matejdro.wearvibrationcenter.mute.AppMuteManager
import com.matejdro.wearvibrationcenter.mute.TimedMuteManager
import com.matejdro.wearvibrationcenter.notificationprovider.NotificationBroadcaster
import com.matejdro.wearvibrationcenter.preferences.GlobalSettings
import com.matejdro.wearvibrationcenter.preferences.PerAppSettings
import com.matejdro.wearvibrationcenter.preferences.PerAppSharedPreferences
import com.matejdro.wearvibrationcenter.preferences.VibrationType
import dagger.hilt.EntryPoints
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class NotificationService : NotificationListenerService() {
    private val pendingNotifications = LinkedList<ProcessedNotification>()
    private lateinit var handler: Handler

    @Inject
    lateinit var builder: NotificationServiceComponent.Builder

    /**
     * To determine whether notification is new or just an update of old notification,
     * we need a list of previous notifications before new one appeared
     */
    private var previousList = emptyArray<StatusBarNotification>()
    private lateinit var processor: NotificationProcessor
    private lateinit var timedMuteManager: TimedMuteManager
    private lateinit var appMuteManager: AppMuteManager
    private lateinit var notificationBroadcaster: NotificationBroadcaster
    lateinit var globalSettings: SharedPreferences

    override fun onDestroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        timedMuteManager.onDestroy()
        appMuteManager.onDestroy()
        notificationBroadcaster.onDestroy()
        super.onDestroy()
        Timber.d("Notification Listener stopped...")
    }

    override fun onCreate() {
        Timber.d("Creating Notification Listener...")
        active = true
        super.onCreate()
        val component = builder.service(this).build()
        val entryPoint = EntryPoints.get(component, NotificationServiceEntryPoint::class.java)
        handler = NotificationRefreshHandler(this)
        globalSettings = PreferenceManager.getDefaultSharedPreferences(this)
        processor = entryPoint.createNotificationProcessor()
        timedMuteManager = entryPoint.createTimedMuteManager()
        appMuteManager = entryPoint.createAppMuteManager()
        notificationBroadcaster = entryPoint.createNotificationBroadcaster()
        scheduleActiveListUpdate()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Timber.d("Got new jellybean notification %s", sbn.packageName)
        scheduleActiveListUpdate()
        if (timedMuteManager.isMutedCurrently) {
            Timber.d("Notification filtered: timed mute ")
            return
        }
        if (!Preferences.getBoolean(globalSettings, GlobalSettings.ENABLE_VIBRATIONS)) {
            Timber.d("Notification filtered: global toggle disable")
            return
        }
        val notificationPreferences =
            PerAppSharedPreferences.getPerAppSharedPreferences(this, sbn.packageName)
        val processedNotification = ProcessedNotification(sbn, notificationPreferences)
        fillMetadata(processedNotification)
        if (appMuteManager.isNotificationMuted(processedNotification)) {
            Timber.d("Notification filtered: app mute")
            return
        }
        val message = Message.obtain()
        message.what = MESSAGE_ADD_NOTIFICATION
        message.obj = processedNotification
        handler.sendMessage(message)
    }

    override fun onNotificationRemoved(removedNotification: StatusBarNotification) {
        Timber.d(
            "Got jellybean dismiss %s %d",
            removedNotification.packageName,
            removedNotification.id
        )
        val message = Message.obtain()
        message.what = MESSAGE_REMOVE_NOTIFICATION
        message.obj = removedNotification
        handler.sendMessage(message)
        scheduleActiveListUpdate()
    }

    override fun getActiveNotifications(): Array<StatusBarNotification> {
        return try {
            val notifications = super.getActiveNotifications()
            notifications ?: emptyArray()
        } catch (ignored: Exception) {
            // Sometimes notification service will throw internal exception
            Timber.w("Notification service threw error!")
            emptyArray()
        }
    }

    private fun fillMetadata(notification: ProcessedNotification) {
        for (other in previousList) {
            if (notification.containsSameNotification(other)) {
                notification.isUpdateNotification = true
                break
            }
        }
        if (!notification.isUpdateNotification) {
            for (other in previousList) {
                if (notification.contentNotification.packageName == other.packageName) {
                    notification.isSubsequentNotification = true
                    break
                }
            }
        }
        NotificationTextParser(this).parse(notification, notification.contentNotification)
    }

    private fun addNotificationSync(notification: ProcessedNotification) {
        if (Preferences.getEnum(
                notification.appPreferences,
                PerAppSettings.VIBRATION_TYPE
            ) == VibrationType.NONE
        ) {
            Timber.d("Notification filtered: vibration type")
            return
        }
        pendingNotifications.push(notification)

        // at least 250 millisecond delay between notification posting and processing
        // is enforced to catch all notifications in the wear group
        var procesingDelay = Preferences.getInt(globalSettings, GlobalSettings.PROCESSING_DELAY)
        procesingDelay = max(250.0, procesingDelay.toDouble()).toInt()
        handler.removeMessages(MESSAGE_PROCESS_PENDING)
        handler.sendMessageDelayed(
            Message.obtain(handler, MESSAGE_PROCESS_PENDING),
            procesingDelay.toLong()
        )
    }

    private fun removeNotificationSync(removedSbn: StatusBarNotification) {
        val i = pendingNotifications.iterator()
        while (i.hasNext()) {
            if (i.next().containsSameNotification(removedSbn)) {
                i.remove()
            }
        }
    }

    private fun processPendingSync() {
        processor.processPendingNotifications(pendingNotifications)
        pendingNotifications.clear()
    }

    private fun scheduleActiveListUpdate() {
        handler.removeMessages(MESSAGE_UPDATE_ACTIVE_LIST)
        handler.sendEmptyMessageDelayed(MESSAGE_UPDATE_ACTIVE_LIST, 250)
    }

    private fun updateActiveList() {
        previousList = activeNotifications
    }

    private class NotificationRefreshHandler(service: NotificationService) : Handler(Looper.getMainLooper()) {
        private val service: WeakReference<NotificationService>

        init {
            this.service = WeakReference(service)
        }

        override fun handleMessage(msg: Message) {
            val listener = service.get() ?: return
            when (msg.what) {
                MESSAGE_PROCESS_PENDING -> listener.processPendingSync()
                MESSAGE_ADD_NOTIFICATION -> listener.addNotificationSync(msg.obj as ProcessedNotification)
                MESSAGE_REMOVE_NOTIFICATION -> listener.removeNotificationSync(msg.obj as StatusBarNotification)
                MESSAGE_UPDATE_ACTIVE_LIST -> listener.updateActiveList()
            }
        }
    }

    companion object {
        const val MESSAGE_PROCESS_PENDING = 0
        const val MESSAGE_ADD_NOTIFICATION = 1
        const val MESSAGE_REMOVE_NOTIFICATION = 2
        const val MESSAGE_UPDATE_ACTIVE_LIST = 3
        @JvmField
        var active = false
    }
}
