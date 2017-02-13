package com.matejdro.wearvibrationcenter;

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
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.wearable.intent.RemoteIntent;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearvibrationcenter.mutepicker.AppMuteActivity;
import com.matejdro.wearvibrationcenter.mutepicker.MuteModeActivity;
import com.matejdro.wearvibrationcenter.mutepicker.TimedMuteActivity;

public class PhoneAppNoticeActivity extends Activity {
    private View noPhoneErrorView;
    private View loadingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_app_notice);

        noPhoneErrorView = findViewById(R.id.no_phone_error_view);
        loadingView = findViewById(R.id.progress);
    }

    public void openPhonePlayStore(View view) {
        Intent playStoreIntent = new Intent(Intent.ACTION_VIEW);
        playStoreIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        playStoreIntent.setData(Uri.parse("market://details?id=" + getPackageName()));

        ResultReceiver resultReceiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                Intent confirmationIntent = new Intent(PhoneAppNoticeActivity.this, ConfirmationActivity.class);

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

        noPhoneErrorView.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);
    }
}
