package com.matejdro.wearvibrationcenter.mutepicker;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.activity.ConfirmationActivity;
import android.support.wearable.view.DefaultOffsettingHelper;
import android.support.wearable.view.WearableRecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearvibrationcenter.common.TimedMuteCommand;
import com.matejdro.wearutils.messages.MessagingUtils;
import com.matejdro.wearutils.messages.ParcelPacker;
import com.matejdro.wearutils.messages.SingleMessageReceiver;
import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.preferences.GlobalSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimedMuteActivity extends Activity {

    private WearableRecyclerView recycler;
    private ProgressBar progressBar;

    private List<String> muteIntervals;
    private List<String> storedMuteIntervals;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_list_loading);

        muteIntervals = new ArrayList<>();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        storedMuteIntervals = Preferences.getStringList(preferences, GlobalSettings.MUTE_INTERVALS);

        for (String item : storedMuteIntervals) {
            muteIntervals.add(getString(R.string.mute_interval_list_item, item));
        }

        muteIntervals.add(getString(R.string.until_manually));

        recycler = (WearableRecyclerView) findViewById(R.id.recycler);
        progressBar = (ProgressBar) findViewById(R.id.progress);

        ListAdapter adapter = new ListAdapter();
        recycler.setOffsettingHelper(new ListOffsettingHelper());
        recycler.setAdapter(adapter);
    }

    private void itemSelected(int position)
    {
        int duration = -1;

        if (position < storedMuteIntervals.size())
        {
            try {
                duration = Integer.parseInt(storedMuteIntervals.get(position));
            } catch (NumberFormatException ignored) {
            }
        }

        new MuteExecutor(duration).execute((Void) null);
    }

    private class MuteExecutor extends AsyncTask<Void, Void, Boolean>
    {
        private final int muteDurationMinutes;

        public MuteExecutor(int muteDurationMinutes) {
            this.muteDurationMinutes = muteDurationMinutes;
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            GoogleApiClient googleApiClient = new GoogleApiClient.Builder(TimedMuteActivity.this)
                    .addApi(Wearable.API)
                    .build();

            googleApiClient.blockingConnect();

            SingleMessageReceiver ackReceiver = new SingleMessageReceiver(googleApiClient,
                    Uri.parse("wear://*" + CommPaths.COMMAND_RECEIVAL_ACKNOWLEDGMENT),
                    MessageApi.FILTER_LITERAL);

            TimedMuteCommand command = new TimedMuteCommand(muteDurationMinutes);
            Wearable.MessageApi.sendMessage(googleApiClient,
                    MessagingUtils.getOtherNodeId(googleApiClient),
                    CommPaths.COMMAND_TIMED_MUTE,
                    ParcelPacker.getData(command));

            MessageEvent receivedMessage;
            try {
                receivedMessage = ackReceiver.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            } catch (TimeoutException e) {
                return false;
            }

            googleApiClient.disconnect();

            return receivedMessage != null;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Intent intent = new Intent(TimedMuteActivity.this, ConfirmationActivity.class);

            if (success) {
                intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                        ConfirmationActivity.SUCCESS_ANIMATION);
                intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                        getString(R.string.timed_mute_success));
            } else {
                intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                        ConfirmationActivity.FAILURE_ANIMATION);
                intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                        getString(R.string.timed_mute_fail));

            }

            startActivity(intent);
            finish();
        }
    }

    private class ListAdapter extends WearableRecyclerView.Adapter<ListViewHolder> {

        public ListAdapter() {
        }

        @Override
        public ListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_mute_time, parent, false);

            ListViewHolder holder = new ListViewHolder(view);
            holder.textView = (TextView) view;
            return holder;
        }

        @Override
        public void onBindViewHolder(ListViewHolder holder, int position) {
            holder.textView.setText(muteIntervals.get(position));
        }

        @Override
        public int getItemCount() {
            return muteIntervals.size();
        }
    }

    private class ListOffsettingHelper extends DefaultOffsettingHelper {

        @Override
        public void updateChild(View child, WearableRecyclerView parent) {
            super.updateChild(child, parent);

            // Figure out % progress from top to bottom
            float centerOffset = ((float) child.getHeight() / 2.0f) /  (float) recycler.getHeight();
            float yRelativeToCenterOffset = (child.getY() / recycler.getHeight()) + centerOffset;

            // Normalize for center
            float progressToCenter = Math.abs(0.5f - yRelativeToCenterOffset) * 2;
            progressToCenter = Math.min(0.8f, progressToCenter);

            child.setAlpha(1 - progressToCenter);
        }
    }

    private class ListViewHolder extends WearableRecyclerView.ViewHolder implements View.OnClickListener {

        public ListViewHolder(View itemView) {
            super(itemView);

            itemView.setOnClickListener(this);
        }

        public TextView textView;

        @Override
        public void onClick(View v) {
            itemSelected(getAdapterPosition());
        }
    }
}
