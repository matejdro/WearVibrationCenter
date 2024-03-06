package com.matejdro.wearvibrationcenter.mutepicker

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
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
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearremotelist.parcelables.CompressedParcelableBitmap
import com.matejdro.wearremotelist.parcelables.StringParcelableWraper
import com.matejdro.wearremotelist.receiverside.RemoteList
import com.matejdro.wearremotelist.receiverside.RemoteListListener
import com.matejdro.wearremotelist.receiverside.RemoteListManager
import com.matejdro.wearremotelist.receiverside.conn.WatchSingleConnection
import com.matejdro.wearutils.messages.ParcelPacker
import com.matejdro.wearutils.messages.awaitFirstMessage
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearvibrationcenter.R
import com.matejdro.wearvibrationcenter.common.AppMuteCommand
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.preferences.GlobalWatchPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min

class AppMuteActivity : ComponentActivity(), RemoteListListener {
    private lateinit var recycler: WearableRecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyNotice: TextView
    private lateinit var listConnection: WatchSingleConnection
    private lateinit var preferences: SharedPreferences
    private lateinit var imageList: RemoteList<CompressedParcelableBitmap>
    private lateinit var textList: RemoteList<StringParcelableWraper>
    private lateinit var adapter: ListAdapter
    private var positionPendingConfirmation = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_mute)
        recycler = findViewById<View>(R.id.recycler) as WearableRecyclerView
        progressBar = findViewById<View>(R.id.progress) as ProgressBar
        emptyNotice = findViewById<View>(R.id.empty_notice) as TextView
        adapter = ListAdapter()
        recycler.offsettingHelper = ListOffsettingHelper()
        recycler.setItemAnimator(DefaultItemAnimatorNoChange())
        recycler.centerEdgeItems = true
        recycler.setAdapter(adapter)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        listConnection = WatchSingleConnection(
            Wearable.getMessageClient(this),
            Wearable.getNodeClient(this)
        )
        val listManager = RemoteListManager(listConnection, this)
        textList = listManager.createRemoteList(
            CommPaths.LIST_ACTIVE_APPS_NAMES,
            StringParcelableWraper.CREATOR,
            1000,
            40,
            lifecycleScope
        )
        imageList = listManager.createRemoteList(
            CommPaths.LIST_ACTIVE_APPS_ICONS,
            CompressedParcelableBitmap.CREATOR,
            200,
            5,
            lifecycleScope
        )
        textList.priority = 1
        imageList.priority = 0
    }

    override fun onStart() {
        super.onStart()
        showLoading()
    }

    override fun onStop() {
        listConnection.disconnect()
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
        progressBar.visibility = View.VISIBLE
        recycler.visibility = View.GONE

        lifecycleScope.launch {
            val messageClient = Wearable.getMessageClient(this@AppMuteActivity)
            val nodeClient = Wearable.getNodeClient(this@AppMuteActivity)

            val command = AppMuteCommand(position)
            messageClient.sendMessageToNearestClient(
                nodeClient,
                CommPaths.COMMAND_APP_MUTE,
                ParcelPacker.getData(command)
            )

            val receivedMessage = withTimeoutOrNull(2_000) {
                messageClient.awaitFirstMessage(
                    Uri.parse("wear://*" + CommPaths.COMMAND_RECEIVAL_ACKNOWLEDGMENT),
                    MessageClient.FILTER_LITERAL
                )
            }

            val intent = Intent(this@AppMuteActivity, ConfirmationActivity::class.java)
            if (receivedMessage != null) {
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CONFIRMATION_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
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
                lifecycleScope.launch {
                    textList.fillAround(position, 20)
                }
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
