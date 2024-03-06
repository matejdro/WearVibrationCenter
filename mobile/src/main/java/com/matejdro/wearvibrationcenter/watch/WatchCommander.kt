package com.matejdro.wearvibrationcenter.watch

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.preference.PreferenceManager
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.messages.ParcelPacker
import com.matejdro.wearutils.messages.getOtherNodeId
import com.matejdro.wearutils.preferencesync.PreferencePusher.pushPreferences
import com.matejdro.wearvibrationcenter.common.AlarmCommand
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.common.LiteAlarmCommand
import com.matejdro.wearvibrationcenter.common.VibrationCommand

class WatchCommander : IntentService("WatchCommander") {
    private lateinit var googleApiClient: GoogleApiClient

    @Deprecated("Deprecated in Java")
    override fun onCreate() {
        super.onCreate()
        googleApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .build()
        googleApiClient.connect()
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        googleApiClient.disconnect()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }

        if (!ensurePlayServicesConnection()) {
            return
        }
        when (intent.action) {
            ACTION_VIBRATE_COMMAND -> vibrationCommand(intent)
            ACTION_ALARM_COMMAND -> alarmCommand(intent)
            ACTION_TRANSMIT_GLOBAL_SETTINGS -> transmitGlobalSettings()
        }
    }

    private fun vibrationCommand(intent: Intent) {
        val commandData = intent.getParcelableExtra<Parcelable>(KEY_COMMAND_DATA) ?: return
        val watchId = getOtherNodeId(googleApiClient) ?: return
        Wearable.MessageApi.sendMessage(
            googleApiClient, watchId, CommPaths.COMMAND_VIBRATE, ParcelPacker.getData(
                commandData
            )
        ).await()
    }

    private fun alarmCommand(intent: Intent) {
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
        Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).await()
    }

    private fun transmitGlobalSettings() {
        val globalSettings = PreferenceManager.getDefaultSharedPreferences(this)
        pushPreferences(
            googleApiClient,
            globalSettings,
            CommPaths.PREFERENCES_PREFIX,
            false
        ).await()
    }

    private fun ensurePlayServicesConnection(): Boolean {
        val result = googleApiClient.blockingConnect()
        if (!result.isSuccess) {
            GoogleApiAvailability.getInstance().showErrorNotification(this, result)
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
