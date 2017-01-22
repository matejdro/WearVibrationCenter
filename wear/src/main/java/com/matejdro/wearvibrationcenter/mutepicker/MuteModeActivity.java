package com.matejdro.wearvibrationcenter.mutepicker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.matejdro.wearvibrationcenter.R;

public class MuteModeActivity extends Activity {
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
}
