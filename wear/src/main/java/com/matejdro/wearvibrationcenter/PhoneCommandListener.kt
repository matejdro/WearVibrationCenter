package com.matejdro.wearvibrationcenter

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Vibrator
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItemAsset
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.matejdro.wearutils.WatchUtils
import com.matejdro.wearutils.logging.LogTransmitter
import com.matejdro.wearutils.messages.ParcelPacker
import com.matejdro.wearvibrationcenter.common.AlarmCommand
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.common.InterruptionCommand
import com.matejdro.wearvibrationcenter.common.LiteAlarmCommand
import com.matejdro.wearvibrationcenter.common.VibrationCommand
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class PhoneCommandListener : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (CommPaths.COMMAND_VIBRATE == messageEvent.path) {
            val vibrationCommand =
                ParcelPacker.getParcelable(messageEvent.data, VibrationCommand.CREATOR)
            vibrate(vibrationCommand)
        } else if (CommPaths.COMMAND_SEND_LOGS == messageEvent.path) {
            val logSendingIntent = Intent(this, LogTransmitter::class.java)
            logSendingIntent.putExtra(
                LogTransmitter.EXTRA_TARGET_NODE_ID,
                messageEvent.sourceNodeId
            )
            logSendingIntent.putExtra(LogTransmitter.EXTRA_TARGET_PATH, CommPaths.CHANNEL_LOGS)
            startService(logSendingIntent)
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        val dataClient = Wearable.getDataClient(this)

        for (event in dataEventBuffer) {
            if (event.type != DataEvent.TYPE_CHANGED) {
                continue
            }
            val dataItem = event.dataItem
            if (CommPaths.COMMAND_ALARM == dataItem.uri.path) {
                val data = dataItem.data ?: return
                val liteAlarmCommand = ParcelPacker.getParcelable(
                    data, LiteAlarmCommand.CREATOR
                )
                val iconData =
                    getByteArrayAsset(dataItem.assets[CommPaths.ASSET_ICON], dataClient)
                val backgroundData =
                    getByteArrayAsset(dataItem.assets[CommPaths.ASSET_BACKGROUND], dataClient)
                val alarmCommand = AlarmCommand(liteAlarmCommand, backgroundData, iconData)
                alarm(alarmCommand)
                dataClient.deleteDataItems(dataItem.uri)
            }
        }
    }

    private fun vibrate(vibrationCommand: VibrationCommand) {
        if (!filterCommand(vibrationCommand)) {
            return
        }
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(vibrationCommand.pattern, -1)
        if (vibrationCommand.shouldForceTurnScreenOn()) {
            // Acquire very brief screen wakelock to wake the screen up
            val pm = (getSystemService(POWER_SERVICE) as PowerManager)
            @Suppress("deprecation") val wakeLock// There appears to be no non-deprecated way to do this
                    = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK
                        or PowerManager.ACQUIRE_CAUSES_WAKEUP
                        or PowerManager.ON_AFTER_RELEASE, "Vibration Center screen wake"
            )
            wakeLock.acquire(1)
        }
    }

    private fun alarm(alarmCommand: AlarmCommand) {
        if (!filterCommand(alarmCommand)) {
            return
        }
        val alarmActivityIntent = Intent(this, AlarmActivity::class.java)
        alarmActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        alarmActivityIntent.putExtra(
            AlarmActivity.EXTRA_ALARM_COMMAND_BYTES,
            ParcelPacker.getData(alarmCommand)
        )
        startActivity(alarmActivityIntent)
    }

    private fun filterCommand(command: InterruptionCommand): Boolean {
        if (command.shouldNotVibrateOnCharger()) {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val pluggedIn = batteryIntent != null && batteryIntent.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                0
            ) != 0
            if (pluggedIn) {
                Timber.d("Filter - Charger!")
                return false
            }
        }
        if (command.shouldNotVibrateInTheater() &&
            WatchUtils.isWatchInTheaterMode(this)
        ) {
            Timber.d("Filter - Theater mode!")
            return false
        }
        return true
    }

    private fun getByteArrayAsset(
        asset: DataItemAsset?,
        dataClient: DataClient
    ): ByteArray? {
        if (asset == null) {
            return null
        }
        val inputStream =
            Tasks.await(dataClient.getFdForAsset(asset)).inputStream
        val data = readFully(inputStream)
        if (data != null) {
            try {
                inputStream.close()
            } catch (ignored: IOException) {
            }
        }
        return data
    }

    companion object {
        private fun readFully(`in`: InputStream?): ByteArray? {
            if (`in` == null) {
                return null
            }
            val outStream = ByteArrayOutputStream()
            var bytesRead: Int
            val buffer = ByteArray(1024)
            try {
                while (`in`.read(buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
            } catch (ignored: IOException) {
                return null
            }
            return outStream.toByteArray()
        }
    }
}
