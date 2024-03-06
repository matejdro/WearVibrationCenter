package com.matejdro.wearvibrationcenter.mutepicker

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.wearable.activity.ConfirmationActivity
import android.support.wearable.view.DefaultOffsettingHelper
import android.support.wearable.view.WearableRecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearremotelist.parcelables.CompressedParcelableBitmap
import com.matejdro.wearremotelist.parcelables.StringParcelableWraper
import com.matejdro.wearremotelist.receiverside.RemoteList
import com.matejdro.wearremotelist.receiverside.RemoteListListener
import com.matejdro.wearremotelist.receiverside.RemoteListManager
import com.matejdro.wearremotelist.receiverside.conn.WatchSingleConnection
import com.matejdro.wearutils.messages.ParcelPacker
import com.matejdro.wearutils.messages.SingleMessageReceiver
import com.matejdro.wearutils.messages.getOtherNodeId
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearvibrationcenter.R
import com.matejdro.wearvibrationcenter.common.AppMuteCommand
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.preferences.GlobalWatchPreferences
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.abs
import kotlin.math.min

class AppMuteActivity : Activity(), GoogleApiClient.ConnectionCallbacks, RemoteListListener {
    private lateinit var recycler: WearableRecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyNotice: TextView
    private lateinit var googleApiClient: GoogleApiClient
    private lateinit var listConnection: WatchSingleConnection
    private lateinit var preferences: SharedPreferences
    private lateinit var imageList: RemoteList<CompressedParcelableBitmap>
    private lateinit var textList: RemoteList<StringParcelableWraper>
    private lateinit var adapter: ListAdapter
    private var positionPendingConfirmation = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_mute)
        googleApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .build()
        recycler = findViewById<View>(R.id.recycler) as WearableRecyclerView
        progressBar = findViewById<View>(R.id.progress) as ProgressBar
        emptyNotice = findViewById<View>(R.id.empty_notice) as TextView
        adapter = ListAdapter()
        recycler.offsettingHelper = ListOffsettingHelper()
        recycler.setItemAnimator(DefaultItemAnimatorNoChange())
        recycler.centerEdgeItems = true
        recycler.setAdapter(adapter)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onStart() {
        super.onStart()
        showLoading()
        googleApiClient.connect()
    }

    override fun onStop() {
        listConnection.disconnect()
        googleApiClient.disconnect()
        super.onStop()
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        recycler.visibility = View.GONE
        emptyNotice.visibility = View.GONE
    }

    private fun showList() {
        progressBar.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        emptyNotice.visibility = View.GONE
    }

    private fun showEmptyNotice() {
        progressBar.visibility = View.GONE
        recycler.visibility = View.GONE
        emptyNotice.visibility = View.VISIBLE
    }

    private fun itemSelected(position: Int) {
        if (Preferences.getBoolean(
                preferences,
                GlobalWatchPreferences.DO_NOT_ASK_APP_MUTE_CONFIRM
            )
        ) {
            muteApp(position)
            return
        }
        positionPendingConfirmation = position
        val confirmationIntent = Intent(this, AppMuteConfirmationActivity::class.java)
        confirmationIntent.putExtra(
            AppMuteConfirmationActivity.EXTRA_APP_NAME,
            textList[position].string
        )
        startActivityForResult(confirmationIntent, CONFIRMATION_REQUEST_CODE)
    }

    private fun muteApp(position: Int) {
        MuteExecutor(position).execute(null as Void?)
    }

    override fun onConnected(bundle: Bundle?) {
        listConnection = WatchSingleConnection(googleApiClient)
        val listManager = RemoteListManager(listConnection, this)
        textList = listManager.createRemoteList(
            CommPaths.LIST_ACTIVE_APPS_NAMES,
            StringParcelableWraper.CREATOR,
            1000,
            40
        )
        imageList = listManager.createRemoteList(
            CommPaths.LIST_ACTIVE_APPS_ICONS,
            CompressedParcelableBitmap.CREATOR,
            200,
            5
        )
        textList.setPriority(1)
        imageList.setPriority(0)
    }

    override fun onConnectionSuspended(i: Int) {}
    override fun onListSizeChanged(listPath: String) {
        runOnUiThread {
            if (textList.size() == 0) {
                showEmptyNotice()
            } else {
                showList()
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun newEntriesTransferred(listPath: String, from: Int, to: Int) {
        runOnUiThread { adapter.notifyItemRangeChanged(from, to - from + 1) }
    }

    override fun onError(listPath: String, errorCode: Int) {}
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CONFIRMATION_REQUEST_CODE && resultCode == RESULT_OK) {
            val doNotAskAgain =
                data.getBooleanExtra(AppMuteConfirmationActivity.EXTRA_DO_NOT_ASK_AGAIN, false)
            if (doNotAskAgain) {
                val editor = preferences.edit()
                Preferences.putBoolean(
                    editor,
                    GlobalWatchPreferences.DO_NOT_ASK_APP_MUTE_CONFIRM,
                    true
                )
                editor.apply()
            }
            muteApp(positionPendingConfirmation)
        }
    }

    private inner class MuteExecutor(private val appIndex: Int) :
        AsyncTask<Void?, Void?, Boolean>() {
        @Deprecated("Deprecated in Java")
        override fun onPreExecute() {
            progressBar.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        }

        @Deprecated("Deprecated in Java")
        protected override fun doInBackground(vararg params: Void?): Boolean? {
            val googleApiClient = GoogleApiClient.Builder(this@AppMuteActivity)
                .addApi(Wearable.API)
                .build()
            googleApiClient.blockingConnect()
            val ackReceiver = SingleMessageReceiver(
                googleApiClient,
                Uri.parse("wear://*" + CommPaths.COMMAND_RECEIVAL_ACKNOWLEDGMENT),
                MessageApi.FILTER_LITERAL
            )
            val command = AppMuteCommand(appIndex)
            Wearable.MessageApi.sendMessage(
                googleApiClient,
                getOtherNodeId(googleApiClient)!!,
                CommPaths.COMMAND_APP_MUTE,
                ParcelPacker.getData(command)
            )
            val receivedMessage: MessageEvent?
            receivedMessage = try {
                ackReceiver[2, TimeUnit.SECONDS]
            } catch (e: InterruptedException) {
                return false
            } catch (e: TimeoutException) {
                return false
            }
            googleApiClient.disconnect()
            return receivedMessage != null
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(success: Boolean) {
            val intent = Intent(this@AppMuteActivity, ConfirmationActivity::class.java)
            if (success) {
                intent.putExtra(
                    ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                    ConfirmationActivity.SUCCESS_ANIMATION
                )
                intent.putExtra(
                    ConfirmationActivity.EXTRA_MESSAGE,
                    getString(R.string.app_muted)
                )
            } else {
                intent.putExtra(
                    ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                    ConfirmationActivity.FAILURE_ANIMATION
                )
                intent.putExtra(
                    ConfirmationActivity.EXTRA_MESSAGE,
                    getString(R.string.timed_mute_fail)
                )
            }
            startActivity(intent)
            finish()
        }
    }

    private inner class ListAdapter : RecyclerView.Adapter<ListViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
            val view = layoutInflater.inflate(R.layout.item_mute_app, parent, false) as ViewGroup
            val holder: ListViewHolder = ListViewHolder(view)
            holder.textView = view.findViewById<View>(android.R.id.text1) as TextView
            holder.imageView = view.findViewById<View>(R.id.image) as ImageView
            view.tag = holder
            return holder
        }

        override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
            val text = textList[position]
            if (text == null) {
                holder.textView.setText(null)
                holder.imageView.setImageDrawable(null)
            } else {
                holder.textView.text = text.string

                //Make sure there is plenty of text items loaded around in case user scrolls fast.
                textList.fillAround(position, 20)
                val image = imageList[position]
                if (image == null) holder.imageView.setImageDrawable(null) else holder.imageView.setImageBitmap(
                    image.bitmap
                )
            }
        }

        override fun getItemCount(): Int {
            return min(
                textList.size().toDouble(),
                imageList.size().toDouble()
            ).toInt()
        }
    }

    private inner class ListOffsettingHelper internal constructor() : DefaultOffsettingHelper() {
        private val roundScreen: Boolean

        init {
            roundScreen = resources.configuration.isScreenRound
        }

        override fun updateChild(child: View, parent: WearableRecyclerView) {
            super.updateChild(child, parent)

            // Figure out % progress from top to bottom
            val centerOffset = child.height.toFloat() / 2.0f / recycler.height.toFloat()
            val yRelativeToCenterOffset = child.y / recycler.height + centerOffset

            // Normalize for center
            var progressToCenter = abs((0.5f - yRelativeToCenterOffset).toDouble())
                .toFloat()
            progressToCenter = min(0.5, progressToCenter.toDouble()).toFloat()
            val holder = child.tag as ListViewHolder
            val scale = (1 - progressToCenter).toFloat()
            child.setAlpha(scale)
            if (roundScreen) {
                holder.imageView.scaleX = scale
                holder.imageView.scaleY = scale
            }
        }
    }

    private inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        lateinit var textView: TextView
        lateinit var imageView: ImageView

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            itemSelected(adapterPosition)
        }
    }

    private class DefaultItemAnimatorNoChange : DefaultItemAnimator() {
        init {
            // Item change animation causes glitches while scrolling. Disable.
            supportsChangeAnimations = false
        }
    }

    companion object {
        private const val CONFIRMATION_REQUEST_CODE = 0
    }
}
