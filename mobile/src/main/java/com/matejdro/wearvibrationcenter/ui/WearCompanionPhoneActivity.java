package com.matejdro.wearvibrationcenter.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.google.android.gms.common.api.GoogleApiActivity;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.wearable.intent.RemoteIntent;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.common.CommPaths;

public abstract class WearCompanionPhoneActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, ResultCallback<CapabilityApi.GetCapabilityResult> {
    private GoogleApiClient googleApiClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();
    }

    @Override
    protected void onStart() {
        googleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        googleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.CapabilityApi.getCapability(googleApiClient, getWatchAppPresenceCapability(), CapabilityApi.FILTER_ALL)
                .setResultCallback(this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onResult(@NonNull CapabilityApi.GetCapabilityResult getCapabilityResult) {
        boolean installedOnWatch = !getCapabilityResult.getCapability().getNodes().isEmpty();
        onWatchAppInstalledResult(installedOnWatch);
    }

    protected void onWatchAppInstalledResult(boolean watchAppInstalled) {
        if (watchAppInstalled) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Watch app not installed")
                .setMessage("Watch companion is not installed. This app will not work properly.\n\nIf your watch is running Android Wear 1.x, then you do not have to do anything, app will be installed automatically in few minutes at most.\n\nIf your watch is running Anroid Wear 2, please click 'Open Store' button below to open Play Store on the watch and install companion app.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Open store", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openWatchPlayStorePage();
                    }
                })
                .show();
    }

    protected void openWatchPlayStorePage()  {
        Intent playStoreIntent = new Intent(Intent.ACTION_VIEW);
        playStoreIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        playStoreIntent.setData(Uri.parse("market://details?id=" + getPackageName()));

        RemoteIntent.startRemoteActivity(this, playStoreIntent, null);
    }

    public GoogleApiClient getGoogleApiClient() {
        return googleApiClient;
    }

    public abstract String getWatchAppPresenceCapability();
}
