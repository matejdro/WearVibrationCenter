package com.matejdro.wearvibrationcenter.mutepicker;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import android.support.wearable.activity.ConfirmationActivity;
import android.support.wearable.view.DefaultOffsettingHelper;
import android.support.wearable.view.WearableRecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearremotelist.parcelables.CompressedParcelableBitmap;
import com.matejdro.wearremotelist.parcelables.StringParcelableWraper;
import com.matejdro.wearremotelist.receiverside.RemoteList;
import com.matejdro.wearremotelist.receiverside.RemoteListListener;
import com.matejdro.wearremotelist.receiverside.RemoteListManager;
import com.matejdro.wearremotelist.receiverside.conn.WatchSingleConnection;
import com.matejdro.wearutils.messages.MessagingUtils;
import com.matejdro.wearutils.messages.ParcelPacker;
import com.matejdro.wearutils.messages.SingleMessageReceiver;
import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.common.AppMuteCommand;
import com.matejdro.wearvibrationcenter.common.CommPaths;
import com.matejdro.wearvibrationcenter.preferences.GlobalWatchPreferences;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AppMuteActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, RemoteListListener {
    private static final int CONFIRMATION_REQUEST_CODE = 0;

    private WearableRecyclerView recycler;
    private ProgressBar progressBar;
    private TextView emptyNotice;

    private GoogleApiClient googleApiClient;
    private WatchSingleConnection listConnection;
    private SharedPreferences preferences;

    private RemoteList<CompressedParcelableBitmap> imageList;
    private RemoteList<StringParcelableWraper> textList;

    private ListAdapter adapter;
    private int positionPendingConfirmation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_app_mute);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();

        recycler = (WearableRecyclerView) findViewById(R.id.recycler);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        emptyNotice = (TextView) findViewById(R.id.empty_notice);

        adapter = new ListAdapter();
        recycler.setOffsettingHelper(new ListOffsettingHelper());
        recycler.setItemAnimator(new DefaultItemAnimatorNoChange());
        recycler.setCenterEdgeItems(true);
        recycler.setAdapter(adapter);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        showLoading();
        googleApiClient.connect();
    }

    @Override
    protected void onStop() {
        listConnection.disconnect();
        googleApiClient.disconnect();

        super.onStop();
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        emptyNotice.setVisibility(View.GONE);
    }

    private void showList() {
        progressBar.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        emptyNotice.setVisibility(View.GONE);
    }

    private void showEmptyNotice() {
        progressBar.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyNotice.setVisibility(View.VISIBLE);
    }


    private void itemSelected(int position)
    {
        if (Preferences.getBoolean(preferences, GlobalWatchPreferences.DO_NOT_ASK_APP_MUTE_CONFIRM)) {
            muteApp(position);
            return;
        }

        positionPendingConfirmation = position;

        Intent confirmationIntent = new Intent(this, AppMuteConfirmationActivity.class);
        confirmationIntent.putExtra(AppMuteConfirmationActivity.EXTRA_APP_NAME, textList.get(position).getString());

        startActivityForResult(confirmationIntent, CONFIRMATION_REQUEST_CODE);
    }

    private void muteApp(int position) {
        new MuteExecutor(position).execute((Void) null);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        listConnection = new WatchSingleConnection(googleApiClient);
        RemoteListManager listManager = new RemoteListManager(listConnection, this);

        textList = listManager.createRemoteList(CommPaths.LIST_ACTIVE_APPS_NAMES, StringParcelableWraper.CREATOR, 1000, 40);
        imageList = listManager.createRemoteList(CommPaths.LIST_ACTIVE_APPS_ICONS, CompressedParcelableBitmap.CREATOR, 200, 5);
        textList.setPriority(1);
        imageList.setPriority(0);

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onListSizeChanged(String listPath) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (textList.size() == 0) {
                    showEmptyNotice();
                } else {
                    showList();
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public void newEntriesTransferred(String listPath, final int from, final int to) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.notifyItemRangeChanged(from, to - from + 1);
            }
        });
    }

    @Override
    public void onError(String listPath, int errorCode) {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CONFIRMATION_REQUEST_CODE && resultCode == RESULT_OK) {
            boolean doNotAskAgain = data.getBooleanExtra(AppMuteConfirmationActivity.EXTRA_DO_NOT_ASK_AGAIN, false);
            if (doNotAskAgain) {
                SharedPreferences.Editor editor = preferences.edit();
                Preferences.putBoolean(editor, GlobalWatchPreferences.DO_NOT_ASK_APP_MUTE_CONFIRM, true);
                editor.apply();
            }

            muteApp(positionPendingConfirmation);
        }
    }

    private class MuteExecutor extends AsyncTask<Void, Void, Boolean>
    {
        private final int appIndex;

        public MuteExecutor(int appIndex) {
            this.appIndex = appIndex;
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            GoogleApiClient googleApiClient = new GoogleApiClient.Builder(AppMuteActivity.this)
                    .addApi(Wearable.API)
                    .build();

            googleApiClient.blockingConnect();

            SingleMessageReceiver ackReceiver = new SingleMessageReceiver(googleApiClient,
                    Uri.parse("wear://*" + CommPaths.COMMAND_RECEIVAL_ACKNOWLEDGMENT),
                    MessageApi.FILTER_LITERAL);

            AppMuteCommand command = new AppMuteCommand(appIndex);
            Wearable.MessageApi.sendMessage(googleApiClient,
                    MessagingUtils.getOtherNodeId(googleApiClient),
                    CommPaths.COMMAND_APP_MUTE,
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
            Intent intent = new Intent(AppMuteActivity.this, ConfirmationActivity.class);

            if (success) {
                intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                        ConfirmationActivity.SUCCESS_ANIMATION);
                intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                        getString(R.string.app_muted));
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
            ViewGroup view = (ViewGroup) getLayoutInflater().inflate(R.layout.item_mute_app, parent, false);

            ListViewHolder holder = new ListViewHolder(view);
            holder.textView = (TextView) view.findViewById(android.R.id.text1);
            holder.imageView = (ImageView) view.findViewById(R.id.image);

            view.setTag(holder);

            return holder;
        }

        @Override
        public void onBindViewHolder(ListViewHolder holder, int position) {
            StringParcelableWraper text = textList.get(position);

            if (text == null)
            {
                holder.textView.setText(null);
                holder.imageView.setImageDrawable(null);
            }
            else
            {
                holder.textView.setText(text.getString());

                //Make sure there is plenty of text items loaded around in case user scrolls fast.
                textList.fillAround(position, 20);

                CompressedParcelableBitmap image = imageList.get(position);
                if (image == null)
                    holder.imageView.setImageDrawable(null);
                else
                    holder.imageView.setImageBitmap(image.getBitmap());
            }
        }

        @Override
        public int getItemCount() {
            if (imageList == null || textList == null)
                return 0;

            return Math.min(textList.size(), imageList.size());
        }
    }

    private class ListOffsettingHelper extends DefaultOffsettingHelper {

        private final boolean roundScreen;

        private ListOffsettingHelper() {
            roundScreen = getResources().getConfiguration().isScreenRound();
        }


        @Override
        public void updateChild(View child, WearableRecyclerView parent) {
            super.updateChild(child, parent);

            // Figure out % progress from top to bottom
            float centerOffset = ((float) child.getHeight() / 2.0f) /  (float) recycler.getHeight();
            float yRelativeToCenterOffset = (child.getY() / recycler.getHeight()) + centerOffset;

            // Normalize for center
            float progressToCenter = Math.abs(0.5f - yRelativeToCenterOffset);
            progressToCenter = Math.min(0.5f, progressToCenter);

            ListViewHolder holder = (ListViewHolder) child.getTag();

            float scale = 1 - progressToCenter;
            child.setAlpha(scale);

            if (roundScreen) {
                holder.imageView.setScaleX(scale);
                holder.imageView.setScaleY(scale);
            }
        }
    }

    private class ListViewHolder extends WearableRecyclerView.ViewHolder implements View.OnClickListener {

        public ListViewHolder(View itemView) {
            super(itemView);

            itemView.setOnClickListener(this);
        }

        public TextView textView;
        public ImageView imageView;

        @Override
        public void onClick(View v) {
            itemSelected(getAdapterPosition());
        }
    }

    private static class DefaultItemAnimatorNoChange extends DefaultItemAnimator {
        public DefaultItemAnimatorNoChange() {
            // Item change animation causes glitches while scrolling. Disable.
            setSupportsChangeAnimations(false);
        }
    }

}
