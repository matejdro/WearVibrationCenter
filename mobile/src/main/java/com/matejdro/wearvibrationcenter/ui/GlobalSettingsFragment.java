package com.matejdro.wearvibrationcenter.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearutils.logging.FileLogger;
import com.matejdro.wearutils.logging.LogReceiver;
import com.matejdro.wearutils.messages.MessagingUtils;
import com.matejdro.wearutils.messages.SingleChannelReceiver;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearutils.preferences.CustomStoragePreferenceFragment;
import com.matejdro.wearutils.preferencesync.PreferencePusher;
import com.matejdro.wearvibrationcenter.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.psdev.licensesdialog.LicensesDialog;
import timber.log.Timber;

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
                new LogRetrievalTask().execute((Void) null);
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

    private class LogRetrievalTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog loadingDialog;
        private File zipOutFile;

        @Override
        protected void onPreExecute() {
            loadingDialog = ProgressDialog.show(getActivity(), null, getString(R.string.getting_watch_logs), true);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            GoogleApiClient googleApiClient = new GoogleApiClient.Builder(getActivity())
                    .addApi(Wearable.API)
                    .build();

            ConnectionResult connectionResult = googleApiClient.blockingConnect();
            if (!connectionResult.isSuccess()) {
                GoogleApiAvailability.getInstance().showErrorNotification(getActivity(), connectionResult);
                return false;
            }

            SingleChannelReceiver singleChannelReceiver = new SingleChannelReceiver(googleApiClient);

            Wearable.MessageApi.sendMessage(googleApiClient, MessagingUtils.getOtherNodeId(googleApiClient), CommPaths.COMMAND_SEND_LOGS, null).await();

            Channel channel;
            try {
                channel = singleChannelReceiver.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                return false;
            } catch (TimeoutException ignored) {
                return false;
            }

            boolean success = new LogReceiver(googleApiClient, "watch").receiveLogs(channel);

            channel.close(googleApiClient).await();
            googleApiClient.disconnect();

            FileLogger.getInstance(getActivity()).deactivate();

            File logsFolder = FileLogger.getInstance(getActivity()).getLogsFolder();
            zipOutFile = new File(getActivity().getExternalCacheDir(), "logs.zip");
            ZipOutputStream zipOutputStream = null;
            try {
                zipOutputStream = new ZipOutputStream(new FileOutputStream(zipOutFile));

                for (File file : logsFolder.listFiles()) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zipOutputStream.putNextEntry(zipEntry);

                    byte[] buffer = new byte[1024];
                    FileInputStream inputStream = new FileInputStream(file);
                    int readBytes;
                    while ((readBytes = inputStream.read(buffer)) > 0) {
                        zipOutputStream.write(buffer, 0, readBytes);
                    }
                    inputStream.close();
                }

            } catch (Exception e) {
                Timber.e(e, "Zip writing error");
                return false;
            } finally {
                try {
                    //noinspection ConstantConditions
                    zipOutputStream.close();
                } catch (Exception ignored) {
                }

                FileLogger.getInstance(getActivity()).activate();
            }


            return success;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            loadingDialog.dismiss();

            if (success) {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(zipOutFile));

                intent.setData(Uri.parse(getString(R.string.support_email)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    getActivity().startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.log_sending_failed)
                            .setMessage(R.string.no_email_app_found)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            } else {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.log_sending_failed)
                        .setMessage(R.string.log_retrieval_failed)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }
    }
}
