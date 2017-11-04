package com.matejdro.wearvibrationcenter.mute;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.util.LruCache;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearremotelist.parcelables.CompressedParcelableBitmap;
import com.matejdro.wearremotelist.parcelables.StringParcelableWraper;
import com.matejdro.wearremotelist.providerside.RemoteListProvider;
import com.matejdro.wearremotelist.providerside.conn.PlayServicesConnectionToReceiver;
import com.matejdro.wearutils.messages.ParcelPacker;
import com.matejdro.wearutils.miscutils.BitmapUtils;
import com.matejdro.wearvibrationcenter.common.AppMuteCommand;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearvibrationcenter.notification.NotificationService;
import com.matejdro.wearvibrationcenter.notification.ProcessedNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

public class AppMuteManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, RemoteListProvider, MessageApi.MessageListener {
    private final GoogleApiClient googleApiClient;
    private final NotificationService service;

    private PlayServicesConnectionToReceiver listTransmitter;

    private final Set<String> mutedApps = Collections.synchronizedSet(new HashSet<String>());
    private final List<InstalledApp> appList = new ArrayList<>();
    private final LruCache<String, Bitmap> iconCache;

    private boolean needIconUpdate = true;
    private boolean needTextUpdate = true;

    public AppMuteManager(NotificationService service) {
        this.service = service;

        googleApiClient = new GoogleApiClient.Builder(service)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory());
        iconCache = new LruCache<String, Bitmap>(maxMemory / 32) // 1/16th of device's RAM should be far enough for all icons
        {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

    }

    public void onDestroy() {
        if (googleApiClient.isConnected()) {
            listTransmitter.disconnect();
            Wearable.MessageApi.removeListener(googleApiClient, this);

            googleApiClient.disconnect();
        }

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        listTransmitter = new PlayServicesConnectionToReceiver(googleApiClient, true);
        listTransmitter.setProvider(this);

        Wearable.MessageApi.addListener(googleApiClient, this, Uri.parse("wear://*" + CommPaths.COMMAND_APP_MUTE), MessageApi.FILTER_LITERAL);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        GoogleApiAvailability.getInstance().showErrorNotification(service, connectionResult);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    private void loadAppList() {
        needIconUpdate = true;
        needTextUpdate = true;

        Set<String> addedApps = new HashSet<>();
        appList.clear();
        for (StatusBarNotification notification : service.getActiveNotifications()) {
            String appPackage = notification.getPackageName();
            if (addedApps.contains(appPackage)) {
                continue;
            }

            addedApps.add(appPackage);

            ApplicationInfo applicationInfo;
            try {
                applicationInfo = service.getPackageManager().getApplicationInfo(appPackage, 0);
                String label = service.getPackageManager().getApplicationLabel(applicationInfo).toString();
                appList.add(new InstalledApp(appPackage, label));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        Collections.sort(appList);
    }

    public boolean isNotificationMuted(ProcessedNotification notification) {
        if (!mutedApps.contains(notification.getContentNotification().getPackageName())) {
            return false;
        }

        if (notification.isSubsequentNotification() || notification.isUpdateNotification()) {
            // Notification is either update or subsequent,
            // meaning that user has not yet dismissed all notifications from this app.
            // Lets keep the mute

            return true;
        } else {
            mutedApps.remove(notification.getContentNotification().getPackageName());
            return false;
        }
    }

    private void mute(String appPackage) {
        mutedApps.add(appPackage);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(CommPaths.COMMAND_APP_MUTE)) {
            AppMuteCommand appMuteCommand = ParcelPacker.getParcelable(messageEvent.getData(), AppMuteCommand.CREATOR);

            String mutedAppPackage = appList.get(appMuteCommand.getAppIndex()).packageName;
            mute(mutedAppPackage);

            Wearable.MessageApi.sendMessage(googleApiClient, messageEvent.getSourceNodeId(), CommPaths.COMMAND_RECEIVAL_ACKNOWLEDGMENT, null);
        }
    }

    @Override
    public int getRemoteListSize(String listPath) {
        if (listPath.equals(CommPaths.LIST_ACTIVE_APPS_NAMES)) {
            if (needIconUpdate) {
                loadAppList();

                needTextUpdate = false;
            } else {
                // Once both text and icons have been updated, mark both as them as needing updates
                // to update them on next opening
                needIconUpdate = true;
                needTextUpdate = true;
            }
        } else if (listPath.equals(CommPaths.LIST_ACTIVE_APPS_ICONS)) {
            if (needTextUpdate) {
                loadAppList();
                needIconUpdate = false;
            } else {
                // Once both text and icons have been updated, mark both as them as needing updates
                // to update them on next opening
                needIconUpdate = true;
                needTextUpdate = true;
            }
        } else {
            return -1;
        }

        return appList.size();
    }

    @Override
    public Parcelable getItem(String listPath, int position) {
        if (listPath.equals(CommPaths.LIST_ACTIVE_APPS_NAMES)) {
            return new StringParcelableWraper(appList.get(position).getLabel());
        } else if (listPath.equals(CommPaths.LIST_ACTIVE_APPS_ICONS)) {
            String appPackage = appList.get(position).getPackageName();
            return new CompressedParcelableBitmap(getIcon(appPackage));
        } else {
            return null;
        }
    }

    private Bitmap getIcon(String appPackage) {
        Bitmap storedIcon = iconCache.get(appPackage);
        if (storedIcon != null) {
            return storedIcon;
        }


        try {
            Drawable appIconDrawable = service.getPackageManager().getApplicationIcon(appPackage);
            Bitmap iconBitmap = BitmapUtils.getBitmap(appIconDrawable);
            if (iconBitmap != null) {
                iconBitmap = BitmapUtils.shrinkPreservingRatio(iconBitmap, 64, 64);

                iconCache.put(appPackage, iconBitmap);
                return iconBitmap;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return BitmapUtils.getBitmap(ResourcesCompat.getDrawable(service.getResources(), android.R.drawable.sym_def_app_icon, null));
    }

    @Override
    public void onError(String listPath, int errorCode) {
        Timber.e("Error %s %d", listPath, errorCode);
    }

    private static class InstalledApp implements Comparable<InstalledApp> {
        private final String packageName;
        private final String label;

        public InstalledApp(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public int compareTo(@NonNull InstalledApp o) {
            return label.compareTo(o.label);
        }
    }
}
