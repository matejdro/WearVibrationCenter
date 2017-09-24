package com.matejdro.wearvibrationcenter.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearutils.logging.LogRetrievalTask;
import com.matejdro.wearutils.preferences.CustomStoragePreferenceFragment;
import com.matejdro.wearutils.preferencesync.PreferencePusher;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.common.CommPaths;

import java.io.File;

import de.psdev.licensesdialog.LicensesDialog;

public class GlobalSettingsFragment extends CustomStoragePreferenceFragment implements TitleUtils.TitledFragment {
    private GoogleApiClient googleApiClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.global_settings);

        try {
            findPreference("version").setSummary(getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        findPreference("supportButton").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                sendLogs();
                return true;
            }
        });

        findPreference("licenses").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {

            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                new LicensesDialog.Builder(getActivity())
                        .setNotices(R.raw.notices)
                        .setIncludeOwnLicense(true)
                        .build()
                        .show();
                return true;
            }
        });

        findPreference("donateButton").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=THRX5EMUNBZE6"));
                startActivity(intent);
                return true;
            }
        });

        if (canTransmitSettingsAutomatically()) {
            googleApiClient = new GoogleApiClient.Builder(getActivity())
                    .addApi(Wearable.API)
                    .build();

            googleApiClient.connect();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (canTransmitSettingsAutomatically() && googleApiClient != null && googleApiClient.isConnected()) {
            PreferencePusher.pushPreferences(googleApiClient, getPreferenceManager().getSharedPreferences(), CommPaths.PREFERENCES_PREFIX, false);
        }
    }

    @Override
    public String getTitle() {
        return getString(R.string.global_settings);
    }

    @SuppressWarnings("SameReturnValue")
    protected boolean canTransmitSettingsAutomatically() {
        return true;
    }

    private void sendLogs() {
        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            AlertDialog permissionExplanationDialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.required_permission)
                    .setMessage(R.string.logs_storage_permission_explanation)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @TargetApi(Build.VERSION_CODES.M)
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
                        }
                    })
                    .create();

            permissionExplanationDialog.show();
            return;
        }

        File targetFile = new File(Environment.getExternalStorageDirectory(), "VibrationCenterLogs.log_zip");
        new LogRetrievalTask(getActivity(),
                CommPaths.COMMAND_SEND_LOGS,
                "matejdro@gmail.com",
                targetFile).execute((Void) null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[]
            grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (permissions.length > 0 &&
                permissions[0].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            sendLogs();
        }
    }
}
