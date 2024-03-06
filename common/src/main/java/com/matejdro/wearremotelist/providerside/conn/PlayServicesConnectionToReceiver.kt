package com.matejdro.wearremotelist.providerside.conn

import android.net.Uri
import android.os.Parcel
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.wearable.MessageApi.MessageListener
import com.google.android.gms.wearable.MessageApi.SendMessageResult
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearremotelist.CommonPlayServicesPaths
import com.matejdro.wearremotelist.providerside.RemoteListProvider
import java.nio.ByteBuffer

class PlayServicesConnectionToReceiver(
    googleApiClient: GoogleApiClient?,
    registerListener: Boolean
) : ConnectionToReceiver, MessageListener {
    private val googleApiClient: GoogleApiClient
    private var remoteListProvider: RemoteListProvider? = null

    /**
     * @param googleApiClient Connected GoogleApiClient with Wearable API enabled.
     * @param registerListener If `true`, this class will automatically register itself as message listener onto provided GoogleApiClient.
     * If `false`, you must manually call onMessageReceived() when new messages arrive.
     */
    init {
        if (googleApiClient == null) throw NullPointerException()
        require(googleApiClient.isConnected) { "Provided GoogleApiClient is not connected to Google Play Services." }
        require(googleApiClient.hasConnectedApi(Wearable.API)) { "Provided GoogleApiClient does not have Wearable API connected." }
        this.googleApiClient = googleApiClient
        if (registerListener) Wearable.MessageApi.addListener(googleApiClient, this)
    }

    /**
     * Remove my listener from provided GoogleApiClient.
     */
    fun disconnect() {
        Wearable.MessageApi.removeListener(googleApiClient, this)
    }

    override fun updateListSize(listPath: String, nodeId: String) {
        checkNotNull(remoteListProvider) { "List Provider is still null when trying to send items." }
        val targetUri = CommonPlayServicesPaths.RESPONSE_LIST_SIZE_URI
            .buildUpon()
            .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
            .build()
        val size = remoteListProvider!!.getRemoteListSize(listPath)
        val data = ByteBuffer.allocate(4).putInt(size).array()
        Wearable.MessageApi.sendMessage(googleApiClient, nodeId, targetUri.toString(), data)
            .setResultCallback(ListMessageResultCallback(listPath))
    }

    override fun sendItem(listPath: String, position: Int, nodeId: String) {
        sendItems(listPath, position, position, nodeId)
    }

    override fun sendItems(listPath: String, from: Int, to: Int, nodeId: String) {
        checkNotNull(remoteListProvider) { "List Provider is still null when trying to send items." }
        val targetUri = CommonPlayServicesPaths.RESPONSE_ITEMS_URI
            .buildUpon()
            .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
            .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_FROM, from.toString())
            .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO, to.toString())
            .build()
        val outParcel = Parcel.obtain()
        for (i in from..to) {
            val item = remoteListProvider!!.getItem(listPath, i)
            item.writeToParcel(outParcel, 0)
        }
        val data = outParcel.marshall()
        outParcel.recycle()
        Wearable.MessageApi.sendMessage(googleApiClient, nodeId, targetUri.toString(), data)
            .setResultCallback(ListMessageResultCallback(listPath))
    }

    override fun setProvider(provider: RemoteListProvider) {
        remoteListProvider = provider
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val messageUri = Uri.parse(messageEvent.path)
        if (CommonPlayServicesPaths.REQUEST_LIST_SIZE_URI.path == messageUri.path) {
            val listPath = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH)
            updateListSize(listPath!!, messageEvent.sourceNodeId)
        } else if (CommonPlayServicesPaths.REQUEST_ITEMS_URI.path == messageUri.path) {
            val listPath = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH)
            val from = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_FROM)!!
                .toInt()
            val to = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO)!!
                .toInt()
            sendItems(listPath!!, from, to, messageEvent.sourceNodeId)
        }
    }

    private inner class ListMessageResultCallback(private val listPath: String) :
        ResultCallback<SendMessageResult> {
        override fun onResult(sendMessageResult: SendMessageResult) {
            if (!sendMessageResult.status.isSuccess) {
                if (remoteListProvider != null) remoteListProvider!!.onError(
                    listPath,
                    CommonPlayServicesPaths.getErrorCodeFromGMSStatus(sendMessageResult.status.statusCode)
                )
            }
        }
    }
}
