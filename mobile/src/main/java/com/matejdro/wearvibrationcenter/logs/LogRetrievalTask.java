package com.matejdro.wearvibrationcenter.logs;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearutils.logging.FileLogger;
import com.matejdro.wearutils.logging.LogReceiver;
import com.matejdro.wearutils.messages.MessagingUtils;
import com.matejdro.wearutils.messages.SingleChannelReceiver;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.common.CommPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

public class LogRetrievalTask extends AsyncTask<Void, Void, Boolean> {
    public static final int EMAIL_SENDING_REQUEST_CODE = 58796;

    private Fragment containerFragment;

    private Context context;
    private ProgressDialog loadingDialog;
    private File zipOutFile;

    public LogRetrievalTask(Fragment containerFragment) {
        this.containerFragment = containerFragment;
        this.context = containerFragment.getActivity();
    }

    @Override
    protected void onPreExecute() {
        loadingDialog = ProgressDialog.show(context, null, context.getString(R.string.getting_watch_logs), true);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult = googleApiClient.blockingConnect();
        if (!connectionResult.isSuccess()) {
            GoogleApiAvailability.getInstance().showErrorNotification(context, connectionResult);
            return false;
        }

        SingleChannelReceiver singleChannelReceiver = new SingleChannelReceiver(googleApiClient);

        Wearable.MessageApi.sendMessage(googleApiClient, MessagingUtils.getOtherNodeId(googleApiClient), CommPaths
                .COMMAND_SEND_LOGS, null).await();

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

        FileLogger.getInstance(context).deactivate();

        File logsFolder = FileLogger.getInstance(context).getLogsFolder();
        zipOutFile = new File(Environment.getExternalStorageDirectory(), "/VibrationCenterLogs.log_zip");
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

            FileLogger.getInstance(context).activate();
        }


        return success;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        loadingDialog.dismiss();

        if (success) {
            AlertDialog logsInstructionsDialog = new AlertDialog.Builder(context)
                    .setTitle(R.string.logs_attachment)
                    .setMessage(context.getString(R.string.logs_attachment_explanation))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            showEmailActivity();
                        }
                    })
                    .create();

            logsInstructionsDialog.show();


        } else {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.log_sending_failed)
                    .setMessage(R.string.log_retrieval_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void showEmailActivity() {
        Intent activityIntent = new Intent(Intent.ACTION_SENDTO);
        activityIntent.setType("application/octet-stream");
        activityIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(zipOutFile));

        activityIntent.setData(Uri.parse(context.getString(R.string.support_email)));
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        containerFragment.startActivityForResult(activityIntent, EMAIL_SENDING_REQUEST_CODE);
    }
}
