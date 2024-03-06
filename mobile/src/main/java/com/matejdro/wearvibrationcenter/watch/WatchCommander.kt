package com.matejdro.wearvibrationcenter.watch

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.preference.PreferenceManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.coroutines.await
import com.matejdro.wearutils.messages.ParcelPacker
import com.matejdro.wearutils.messages.getNearestNodeId
import com.matejdro.wearutils.messages.getOtherNodeId
import com.matejdro.wearutils.preferencesync.PreferencePusher.pushPreferences
import com.matejdro.wearvibrationcenter.common.AlarmCommand
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.common.LiteAlarmCommand
import com.matejdro.wearvibrationcenter.common.VibrationCommand
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class WatchCommander : IntentService("WatchCommander") {
    private lateinit var dataClient: DataClient
    private lateinit var messageClient: MessageClient
    private lateinit var nodeClient: NodeClient

    @Deprecated("Deprecated in Java")
    override fun onCreate() {
        super.onCreate()

        dataClient = Wearable.getDataClient(this)
        messageClient = Wearable.getMessageClient(this)
        nodeClient = Wearable.getNodeClient(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) = runBlocking {
        if (intent == null) {
            return@runBlocking
        }

        if (!ensurePlayServicesConnection()) {
            return@runBlocking
        }

        when (intent.action) {
            ACTION_VIBRATE_COMMAND -> vibrationCommand(intent)
            ACTION_ALARM_COMMAND -> alarmCommand(intent)
            ACTION_TRANSMIT_GLOBAL_SETTINGS -> transmitGlobalSettings()
        }
    }

    private suspend fun vibrationCommand(intent: Intent) {
        val commandData = intent.getParcelableExtra<Parcelable>(KEY_COMMAND_DATA) ?: return
        val watchId = nodeClient.getNearestNodeId() ?: return

        messageClient.sendMessage(watchId, CommPaths.COMMAND_VIBRATE, ParcelPacker.getData(
            commandData
        )).await()
    }

    private suspend fun alarmCommand(intent: Intent) {
        val commandData = intent.getParcelableExtra<AlarmCommand>(KEY_COMMAND_DATA)
        val liteAlarmCommand = LiteAlarmCommand(commandData)
        val putDataRequest = PutDataRequest.create(CommPaths.COMMAND_ALARM)
        putDataRequest.setData(ParcelPacker.getData(liteAlarmCommand))
        if (commandData?.icon != null) {
            putDataRequest.putAsset(
                CommPaths.ASSET_ICON, Asset.createFromBytes(
                    commandData.icon
                )
            )
        }
        if (commandData?.backgroundBitmap != null) {
            putDataRequest.putAsset(
                CommPaths.ASSET_BACKGROUND, Asset.createFromBytes(
                    commandData.backgroundBitmap
                )
            )
        }
        putDataRequest.setUrgent()
        dataClient.putDataItem(putDataRequest).await()
    }

    private suspend fun transmitGlobalSettings() {
        val globalSettings = PreferenceManager.getDefaultSharedPreferences(this)
        pushPreferences(
            this,
            globalSettings,
            CommPaths.PREFERENCES_PREFIX,
            false
        )
    }

    private fun ensurePlayServicesConnection(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val available = apiAvailability.isGooglePlayServicesAvailable(this)
        if (available != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().showErrorNotification(this, available)
            return false
        }
        return true
    }

    companion object {
        private const val ACTION_VIBRATE_COMMAND = "VibrateCommand"
        private const val ACTION_ALARM_COMMAND = "AlarmCommand"
        private const val ACTION_TRANSMIT_GLOBAL_SETTINGS = "TransmitGlobalSettings"
        private const val KEY_COMMAND_DATA = "CommandData"
        @JvmStatic
        fun sendVibrationCommand(context: Context, command: VibrationCommand?) {
            val intent = Intent(context, WatchCommander::class.java)
            intent.setAction(ACTION_VIBRATE_COMMAND)
            intent.putExtra(KEY_COMMAND_DATA, command)
            context.startService(intent)
        }

        @JvmStatic
        fun sendAlarmCommand(context: Context, command: AlarmCommand?) {
            val intent = Intent(context, WatchCommander::class.java)
            intent.setAction(ACTION_ALARM_COMMAND)
            intent.putExtra(KEY_COMMAND_DATA, command)
            context.startService(intent)
        }

        @JvmStatic
        fun transmitGlobalSettings(context: Context) {
            val intent = Intent(context, WatchCommander::class.java)
            intent.setAction(ACTION_TRANSMIT_GLOBAL_SETTINGS)
            context.startService(intent)
        }
    }
}
