package com.matejdro.wearvibrationcenter.mute

import android.R
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageApi.MessageListener
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearremotelist.parcelables.CompressedParcelableBitmap
import com.matejdro.wearremotelist.parcelables.StringParcelableWraper
import com.matejdro.wearremotelist.providerside.RemoteListProvider
import com.matejdro.wearremotelist.providerside.conn.PlayServicesConnectionToReceiver
import com.matejdro.wearutils.messages.ParcelPacker
import com.matejdro.wearutils.miscutils.BitmapUtils
import com.matejdro.wearvibrationcenter.common.AppMuteCommand
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.notification.ProcessedNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Collections
import javax.inject.Inject

class AppMuteManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationService: NotificationListenerService
) : GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
    RemoteListProvider, MessageListener {
    private val googleApiClient: GoogleApiClient
    private lateinit var listTransmitter: PlayServicesConnectionToReceiver
    private val mutedApps = Collections.synchronizedSet(HashSet<String>())
    private val appList: MutableList<InstalledApp> = ArrayList()
    private val iconCache: IconCache
    private var needIconUpdate = true
    private var needTextUpdate = true

    init {
        googleApiClient = GoogleApiClient.Builder(context)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build()
        googleApiClient.connect()

        val maxMemory = Runtime.getRuntime().maxMemory().toInt()
        iconCache = IconCache(maxMemory)
    }

    fun onDestroy() {
        if (googleApiClient.isConnected) {
            listTransmitter!!.disconnect()
            Wearable.MessageApi.removeListener(googleApiClient, this)
            googleApiClient.disconnect()
        }
    }

    override fun onConnected(bundle: Bundle?) {
        listTransmitter = PlayServicesConnectionToReceiver(googleApiClient, true)
        listTransmitter!!.setProvider(this)
        Wearable.MessageApi.addListener(
            googleApiClient,
            this,
            Uri.parse("wear://*" + CommPaths.COMMAND_APP_MUTE),
            MessageApi.FILTER_LITERAL
        )
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        GoogleApiAvailability.getInstance().showErrorNotification(
            context, connectionResult
        )
    }

    override fun onConnectionSuspended(i: Int) {}
    private fun loadAppList() {
        needIconUpdate = true
        needTextUpdate = true
        val addedApps: MutableSet<String> = HashSet()
        appList.clear()
        for (notification in notificationService.getActiveNotifications()) {
            val appPackage = notification.packageName
            if (addedApps.contains(appPackage)) {
                continue
            }
            addedApps.add(appPackage)
            var applicationInfo: ApplicationInfo
            try {
                applicationInfo = context.packageManager.getApplicationInfo(appPackage, 0)
                val label = context.packageManager.getApplicationLabel(applicationInfo).toString()
                appList.add(InstalledApp(appPackage, label))
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
        Collections.sort(appList)
    }

    fun isNotificationMuted(notification: ProcessedNotification): Boolean {
        if (!mutedApps.contains(notification.contentNotification.packageName)) {
            return false
        }
        return if (notification.isSubsequentNotification || notification.isUpdateNotification) {
            // Notification is either update or subsequent,
            // meaning that user has not yet dismissed all notifications from this app.
            // Lets keep the mute
            true
        } else {
            mutedApps.remove(notification.contentNotification.packageName)
            false
        }
    }

    private fun mute(appPackage: String) {
        mutedApps.add(appPackage)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == CommPaths.COMMAND_APP_MUTE) {
            val appMuteCommand =
                ParcelPacker.getParcelable(messageEvent.data, AppMuteCommand.CREATOR)
            val mutedAppPackage = appList[appMuteCommand.appIndex].packageName
            mute(mutedAppPackage)
            Wearable.MessageApi.sendMessage(
                googleApiClient,
                messageEvent.sourceNodeId,
                CommPaths.COMMAND_RECEIVAL_ACKNOWLEDGMENT,
                byteArrayOf()
            )
        }
    }

    override fun getRemoteListSize(listPath: String): Int {
        if (listPath == CommPaths.LIST_ACTIVE_APPS_NAMES) {
            if (needIconUpdate) {
                loadAppList()
                needTextUpdate = false
            } else {
                // Once both text and icons have been updated, mark both as them as needing updates
                // to update them on next opening
                needIconUpdate = true
                needTextUpdate = true
            }
        } else if (listPath == CommPaths.LIST_ACTIVE_APPS_ICONS) {
            if (needTextUpdate) {
                loadAppList()
                needIconUpdate = false
            } else {
                // Once both text and icons have been updated, mark both as them as needing updates
                // to update them on next opening
                needIconUpdate = true
                needTextUpdate = true
            }
        } else {
            return -1
        }
        return appList.size
    }

    override fun getItem(listPath: String, position: Int): Parcelable? {
        return if (listPath == CommPaths.LIST_ACTIVE_APPS_NAMES) {
            StringParcelableWraper(appList[position].label)
        } else if (listPath == CommPaths.LIST_ACTIVE_APPS_ICONS) {
            val appPackage: String = appList[position].packageName
            CompressedParcelableBitmap(getIcon(appPackage))
        } else {
            null
        }
    }

    private fun getIcon(appPackage: String): Bitmap? {
        val storedIcon = iconCache[appPackage]
        if (storedIcon != null) {
            return storedIcon
        }
        try {
            val appIconDrawable = context.packageManager.getApplicationIcon(appPackage)
            var iconBitmap = BitmapUtils.getBitmap(appIconDrawable)
            if (iconBitmap != null) {
                iconBitmap = BitmapUtils.shrinkPreservingRatio(iconBitmap, 64, 64)
                iconCache.put(appPackage, iconBitmap)
                return iconBitmap
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return BitmapUtils.getBitmap(
            ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.sym_def_app_icon,
                null
            )
        )
    }

    override fun onError(listPath: String, errorCode: Int) {
        Timber.e("Error %s %d", listPath, errorCode)
    }

    private class InstalledApp(val packageName: String, val label: String) :
        Comparable<InstalledApp> {

        override fun compareTo(o: InstalledApp): Int {
            return label.compareTo(o.label)
        }
    }

    private class IconCache(maxMemory: Int) : LruCache<String?, Bitmap>(
        maxMemory / 32 // 1/16th of device's RAM should be far enough for all icons
    ) {
        override fun sizeOf(key: String?, value: Bitmap): Int {
            return value.getByteCount()
        }
    }
}
