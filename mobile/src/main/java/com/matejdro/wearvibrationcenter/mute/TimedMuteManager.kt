package com.matejdro.wearvibrationcenter.mute

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.messages.ParcelPacker
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearvibrationcenter.R
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.common.TimedMuteCommand
import com.matejdro.wearvibrationcenter.notification.VibrationCenterChannels
import com.matejdro.wearvibrationcenter.preferences.GlobalSettings
import com.matejdro.wearvibrationcenter.preferences.ZenModeChange
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class TimedMuteManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationService: NotificationListenerService,
    preferences: SharedPreferences
) : MessageClient.OnMessageReceivedListener {
    private val messageClient: MessageClient
    private val unmuteReceiver: BroadcastReceiver
    private val preferences: SharedPreferences
    private val alarmManager: AlarmManager
    var isMutedCurrently = false
        private set
    private var previousZenMode = 0
    private val unmutePendingIntent: PendingIntent

    init {
        this.preferences = preferences
        messageClient = Wearable.getMessageClient(context)
        unmuteReceiver = UnmuteReceiver()
        notificationService.registerReceiver(unmuteReceiver, IntentFilter(ACTON_UNMUTE),
            Context.RECEIVER_NOT_EXPORTED)
        alarmManager = notificationService.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        unmutePendingIntent = PendingIntent.getBroadcast(
            notificationService,
            0,
            Intent(ACTON_UNMUTE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        messageClient.addListener(
            this,
            Uri.parse("wear://*" + CommPaths.COMMAND_TIMED_MUTE),
            MessageClient.FILTER_LITERAL
        )
    }

    fun onDestroy() {
        unmute()
        context.unregisterReceiver(unmuteReceiver)
        alarmManager.cancel(unmutePendingIntent)
    }

    private fun mute(timedMuteCommand: TimedMuteCommand) {
        var mutedUntil: Long = -1
        if (timedMuteCommand.muteDurationMinutes > 0) {
            mutedUntil =
                System.currentTimeMillis() + timedMuteCommand.muteDurationMinutes * 1000 * 60
        }
        val builder = NotificationCompat.Builder(
            context,
            VibrationCenterChannels.CHANNEL_TEMPORARY_MUTE
        ).setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setDeleteIntent(unmutePendingIntent)
            .setContentTitle(context.getString(R.string.vibrations_muted_notification_title))
            .setContentText(context.getString(R.string.vibrations_muted_notification_explanation))
        val wearableExtender = NotificationCompat.WearableExtender()
        wearableExtender.addAction(
            NotificationCompat.Action(
                R.drawable.ic_dismiss_mute,
                context.getString(R.string.cancel_mute),
                unmutePendingIntent
            )
        )
        builder.extend(wearableExtender)
        if (mutedUntil > 0) {
            builder.setWhen(mutedUntil).setUsesChronometer(true)

            // NotificationCompat is missing setChronometerCountDown(), lets do it manually.
            builder.getExtras().putBoolean("android.chronometerCountDown", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    mutedUntil,
                    unmutePendingIntent
                )
            } else {
                alarmManager[AlarmManager.RTC_WAKEUP, mutedUntil] = unmutePendingIntent
            }
        }
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_MUTE_DURATION, builder.build())
        isMutedCurrently = true
        previousZenMode = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activateZenMode()
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun activateZenMode() {
        val doNotDistrubChange =
            Preferences.getEnum(preferences, GlobalSettings.TIMED_MUTE_ZEN_CHANGE)
        if (doNotDistrubChange != ZenModeChange.NO_CHANGE) {
            val newZenMode: Int
            newZenMode = when (doNotDistrubChange) {
                ZenModeChange.ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    NotificationListenerService.INTERRUPTION_FILTER_ALARMS
                } else {
                    NotificationListenerService.INTERRUPTION_FILTER_PRIORITY
                }

                ZenModeChange.PRIORITY -> NotificationListenerService.INTERRUPTION_FILTER_PRIORITY
                else -> NotificationListenerService.INTERRUPTION_FILTER_NONE
            }
            previousZenMode = notificationService.getCurrentInterruptionFilter()
            try {
                notificationService.requestInterruptionFilter(newZenMode)
            } catch (e: SecurityException) {
                Timber.w("Notification listener has been disabled before unmute.")
            }
        }
    }

    private fun unmute() {
        println("Unmute! " + isMutedCurrently)
        if (!isMutedCurrently) {
            return
        }
        isMutedCurrently = false
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_MUTE_DURATION)
        alarmManager.cancel(unmutePendingIntent)
        if (previousZenMode != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                notificationService.requestInterruptionFilter(previousZenMode)
            } catch (e: SecurityException) {
                Timber.w("Notification listener has been disabled before unmute.")
            }
            previousZenMode = 0
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == CommPaths.COMMAND_TIMED_MUTE) {
            val timedMuteCommand =
                ParcelPacker.getParcelable(messageEvent.data, TimedMuteCommand.CREATOR)
            messageClient.sendMessage(
                messageEvent.sourceNodeId,
                CommPaths.COMMAND_RECEIVAL_ACKNOWLEDGMENT,
                null
            )

            mute(timedMuteCommand)
        }
    }

    private inner class UnmuteReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            unmute()
        }
    }

    companion object {
        private const val NOTIFICATION_ID_MUTE_DURATION = 0
        private const val ACTON_UNMUTE = "com.matejdro.wearvibrationcenter.action.UNMUTE"
    }
}
