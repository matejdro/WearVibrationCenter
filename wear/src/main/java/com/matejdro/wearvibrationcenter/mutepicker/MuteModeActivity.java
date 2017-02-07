package com.matejdro.wearvibrationcenter.mutepicker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.activity.ConfirmationActivity;
import android.view.View;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.wearable.intent.RemoteIntent;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.common.CommPaths;

public class MuteModeActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, ResultCallback<CapabilityApi.GetCapabilityResult> {
    private View muteModesView;
    private View noPhoneErrorView;
    private View loadingView;

    private GoogleApiClient googleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mute_mode);

        muteModesView = findViewById(R.id.mute_mode_list);
        noPhoneErrorView = findViewById(R.id.no_phone_error_view);
        loadingView = findViewById(R.id.progress);

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

    public void openTimedMute(View view) {
        startActivity(new Intent(this, TimedMuteActivity.class));
        finish();
    }

    public void openAppMute(View view) {
        startActivity(new Intent(this, AppMuteActivity.class));
        finish();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.CapabilityApi.getCapability(googleApiClient, CommPaths.PHONE_APP_CAPABILITY, CapabilityApi.FILTER_ALL)
                .setResultCallback(this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onResult(@NonNull CapabilityApi.GetCapabilityResult getCapabilityResult) {
        boolean installedOnPhone = !getCapabilityResult.getCapability().getNodes().isEmpty();

        if (installedOnPhone) {
            muteModesView.setVisibility(View.VISIBLE);
            noPhoneErrorView.setVisibility(View.GONE);
        } else {
            muteModesView.setVisibility(View.GONE);
            noPhoneErrorView.setVisibility(View.VISIBLE);
        }

        loadingView.setVisibility(View.GONE);
    }

    public void openPhonePlayStore(View view) {
        Intent playStoreIntent = new Intent(Intent.ACTION_VIEW);
        playStoreIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        playStoreIntent.setData(Uri.parse("market://details?id=com.matejdro.wearvibrationcenter"));

        ResultReceiver resultReceiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                Intent confirmationIntent = new Intent(MuteModeActivity.this, ConfirmationActivity.class);

                if (resultCode == RemoteIntent.RESULT_OK) {
                    confirmationIntent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                            ConfirmationActivity.OPEN_ON_PHONE_ANIMATION);
                    confirmationIntent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                            getString(R.string.play_store_opened));
                } else {
                    confirmationIntent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                            ConfirmationActivity.FAILURE_ANIMATION);
                    confirmationIntent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                            getString(R.string.play_store_opening_failed)
                    );

                }

                startActivity(confirmationIntent);
                finish();
            }
        };

        RemoteIntent.startRemoteActivity(this, playStoreIntent, resultReceiver);
        loadingView.setVisibility(View.VISIBLE);
        noPhoneErrorView.setVisibility(View.GONE);
        muteModesView.setVisibility(View.GONE);
    }
}
