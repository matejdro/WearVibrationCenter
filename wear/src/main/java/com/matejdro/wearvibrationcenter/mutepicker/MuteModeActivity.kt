package com.matejdro.wearvibrationcenter.mutepicker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.matejdro.wearutils.companionnotice.WearCompanionWatchActivity;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.common.CommPaths;

public class MuteModeActivity extends WearCompanionWatchActivity implements GoogleApiClient.ConnectionCallbacks, ResultCallback<CapabilityApi.GetCapabilityResult> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mute_mode);
    }

    public void openTimedMute(View view) {
        startActivity(new Intent(this, TimedMuteActivity.class));
        finish();
    }

    public void openAppMute(View view) {
        startActivity(new Intent(this, AppMuteActivity.class));
        finish();
    }

    @Override
    public String getPhoneAppPresenceCapability() {
        return CommPaths.PHONE_APP_CAPABILITY;
    }
}
