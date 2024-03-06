package com.matejdro.wearvibrationcenter.mutepicker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.wearable.activity.ConfirmationActivity
import android.support.wearable.view.DefaultOffsettingHelper
import android.support.wearable.view.WearableRecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.messages.ParcelPacker
import com.matejdro.wearutils.messages.awaitFirstMessage
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearvibrationcenter.R
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.common.TimedMuteCommand
import com.matejdro.wearvibrationcenter.preferences.GlobalSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min

class TimedMuteActivity : ComponentActivity() {
    private lateinit var recycler: WearableRecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var muteIntervals: MutableList<String>
    private lateinit var storedMuteIntervals: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_loading)
        muteIntervals = ArrayList()
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        storedMuteIntervals = Preferences.getStringList(preferences, GlobalSettings.MUTE_INTERVALS)
        for (item in storedMuteIntervals) {
            muteIntervals.add(getString(R.string.mute_interval_list_item, item))
        }
        muteIntervals.add(getString(R.string.until_manually))
        recycler = findViewById<View>(R.id.recycler) as WearableRecyclerView
        progressBar = findViewById<View>(R.id.progress) as ProgressBar
        val adapter: ListAdapter = ListAdapter()
        recycler.offsettingHelper = ListOffsettingHelper()
        recycler.centerEdgeItems = true
        recycler.setAdapter(adapter)
    }

    private fun itemSelected(position: Int) {
        var duration = -1
        if (position < storedMuteIntervals.size) {
            try {
                duration = storedMuteIntervals[position].toInt()
            } catch (ignored: NumberFormatException) {
            }
        }

        progressBar.visibility = View.VISIBLE
        recycler.visibility = View.GONE

        lifecycleScope.launch {
            val messageClient = Wearable.getMessageClient(this@TimedMuteActivity)
            val nodeClient = Wearable.getNodeClient(this@TimedMuteActivity)

            val command = TimedMuteCommand(duration)
            messageClient.sendMessageToNearestClient(
                nodeClient,
                CommPaths.COMMAND_TIMED_MUTE,
                ParcelPacker.getData(command)
            )

            val receivedMessage = withTimeoutOrNull(2_000) {
                messageClient.awaitFirstMessage(
                    Uri.parse("wear://*" + CommPaths.COMMAND_RECEIVAL_ACKNOWLEDGMENT),
                    MessageClient.FILTER_LITERAL
                )
            }

            val intent = Intent(this@TimedMuteActivity, ConfirmationActivity::class.java)
            if (receivedMessage != null) {
                intent.putExtra(
                    ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                    ConfirmationActivity.SUCCESS_ANIMATION
                )
                intent.putExtra(
                    ConfirmationActivity.EXTRA_MESSAGE,
                    getString(R.string.timed_mute_success)
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
            val view = layoutInflater.inflate(R.layout.item_mute_time, parent, false)
            val holder: ListViewHolder = ListViewHolder(view)
            holder.textView = view as TextView
            return holder
        }

        override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
            holder.textView.text = muteIntervals[position]
        }

        override fun getItemCount(): Int {
            return muteIntervals.size
        }
    }

    private inner class ListOffsettingHelper : DefaultOffsettingHelper() {
        override fun updateChild(child: View, parent: WearableRecyclerView) {
            super.updateChild(child, parent)

            // Figure out % progress from top to bottom
            val centerOffset = child.height.toFloat() / 2.0f / recycler.height.toFloat()
            val yRelativeToCenterOffset = child.y / recycler.height + centerOffset

            // Normalize for center
            var progressToCenter = (abs((0.5f - yRelativeToCenterOffset).toDouble()) * 2).toFloat()
            progressToCenter = min(0.8, progressToCenter.toDouble()).toFloat()
            child.setAlpha((1 - progressToCenter).toFloat())
        }
    }

    private inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        lateinit var textView: TextView

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            itemSelected(adapterPosition)
        }
    }
}
