package com.matejdro.wearvibrationcenter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.wearable.activity.WearableActivity;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.wearable.intent.RemoteIntent;

public abstract class WearCompanionWatchActivity extends WearableActivity implements GoogleApiClient.ConnectionCallbacks, ResultCallback<CapabilityApi.GetCapabilityResult> {
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
        Wearable.CapabilityApi.getCapability(googleApiClient, getPhoneAppPresenceCapability(), CapabilityApi.FILTER_ALL)
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

        startActivity(new Intent(this, PhoneAppNoticeActivity.class));
        finish();
    }

    public GoogleApiClient getGoogleApiClient() {
        return googleApiClient;
    }

    public abstract String getPhoneAppPresenceCapability();
}
