package com.matejdro.wearvibrationcenter.ui;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Fragment;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.matejdro.wearvibrationcenter.R;


public class HomeFragment extends Fragment implements TitleUtils.TitledFragment {

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public String getTitle() {
        return getString(R.string.tips_and_tricks);
    }

    @Override
    public void onStart() {
        super.onStart();

        updateTimedMutePermission();
    }

    private void updateTimedMutePermission() {
        View timedMuteView = getView().findViewById(R.id.tip_exact_alarms);
        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Service.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            timedMuteView.setVisibility(View.VISIBLE);
        } else {
            timedMuteView.setVisibility(View.GONE);
        }

        timedMuteView.setOnClickListener((view) -> {
            @SuppressLint("InlinedApi") Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getContext().getPackageName()));
            startActivity(intent);
        });
    }
}
