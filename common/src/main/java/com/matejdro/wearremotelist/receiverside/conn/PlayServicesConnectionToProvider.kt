package com.matejdro.wearremotelist.receiverside.conn

import android.net.Uri
import android.os.AsyncTask
import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.wearable.MessageApi.MessageListener
import com.google.android.gms.wearable.MessageApi.SendMessageResult
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearremotelist.CommonPlayServicesPaths
import com.matejdro.wearremotelist.ErrorListener
import com.matejdro.wearremotelist.receiverside.RemoteListManager.ListDataReceiver
import java.nio.ByteBuffer

/**
 * Class that connects to the provider via Google Play Services's MessageApi.
 */
abstract class PlayServicesConnectionToProvider(googleApiClient: GoogleApiClient?) :
    ConnectionToProvider, MessageListener {
    val googleApiClient: GoogleApiClient
    private var dataReceiver: ListDataReceiver? = null

    /**
     * @param googleApiClient Connected GoogleApiClient with Wearable API enabled.
     */
    init {
        if (googleApiClient == null) throw NullPointerException()
        require(googleApiClient.isConnected) { "Provided GoogleApiClient is not connected to Google Play Services." }
        require(googleApiClient.hasConnectedApi(Wearable.API)) { "Provided GoogleApiClient does not have Wearable API connected." }
        this.googleApiClient = googleApiClient
        Wearable.MessageApi.addListener(googleApiClient, this)
    }

    /**
     * Remove my listener from provided GoogleApiClient.
     */
    fun disconnect() {
        Wearable.MessageApi.removeListener(googleApiClient, this)
    }

    override fun setDataReceiver(dataReceiver: ListDataReceiver) {
        this.dataReceiver = dataReceiver
    }

    abstract fun getProviderNodeId(): String?
    override fun requestListSize(listPath: String) {
        checkNotNull(dataReceiver) { "Data Receiver is still null when transferring messages" }
        object : AsyncTask<Void?, Void?, Void?>() {
            @Deprecated("Deprecated in Java")
            protected override fun doInBackground(vararg params: Void?): Void? {
                val providerNodeId: String = getProviderNodeId()!!
                if (providerNodeId == null) {
                    dataReceiver!!.onError(listPath, ErrorListener.ERROR_DISCONNECTED)
                    return null
                }
                val targetUri = CommonPlayServicesPaths.REQUEST_LIST_SIZE_URI
                    .buildUpon()
                    .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
                    .build()
                Wearable.MessageApi.sendMessage(
                    googleApiClient,
                    providerNodeId,
                    targetUri.toString(),
                    byteArrayOf()
                )
                    .setResultCallback(ListMessageResultCallback(listPath))
                return null
            }
        }.execute(null)
    }

    override fun requestItems(listPath: String, from: Int, to: Int) {
        checkNotNull(dataReceiver) { "Data Receiver is still null when transferring messages" }
        object : AsyncTask<Void?, Void?, Void?>() {
            @Deprecated("Deprecated in Java")
            protected override fun doInBackground(vararg params: Void?): Void? {
                val providerNodeId: String = getProviderNodeId()!!
                if (providerNodeId == null) {
                    dataReceiver!!.onError(listPath, ErrorListener.ERROR_DISCONNECTED)
                    return null
                }
                val targetUri = CommonPlayServicesPaths.REQUEST_ITEMS_URI
                    .buildUpon()
                    .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
                    .appendQueryParameter(
                        CommonPlayServicesPaths.PARAMETER_ITEMS_FROM,
                        from.toString()
                    )
                    .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO, to.toString())
                    .build()
                Wearable.MessageApi.sendMessage(
                    googleApiClient,
                    providerNodeId,
                    targetUri.toString(),
                    byteArrayOf()
                )
                    .setResultCallback(ListMessageResultCallback(listPath))
                return null
            }
        }.execute(null)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        checkNotNull(dataReceiver) { "Data Receiver is still null when messages are arriving." }
        val messageUri = Uri.parse(messageEvent.path)
        if (CommonPlayServicesPaths.RESPONSE_LIST_SIZE_URI.path == messageUri.path) {
            val listPath = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH)
            val newSize = ByteBuffer.wrap(messageEvent.data).getInt()
            if (newSize < 0) {
                dataReceiver!!.onError(listPath, ErrorListener.ERROR_UNKNOWN_LIST)
            } else {
                dataReceiver!!.updateSizeReceived(listPath, newSize)
            }
        } else if (CommonPlayServicesPaths.RESPONSE_ITEMS_URI.path == messageUri.path) {
            val listPath = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH)
            val from = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_FROM)!!
                .toInt()
            val to = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO)!!
                .toInt()
            val parcel = Parcel.obtain()
            parcel.unmarshall(messageEvent.data, 0, messageEvent.data.size)
            parcel.setDataPosition(0)
            val creator = dataReceiver!!.getParcelableCreator(listPath)
            val itemsSent = arrayOfNulls<Parcelable>(to - from + 1)
            for (i in itemsSent.indices) {
                itemsSent[i] = creator.createFromParcel(parcel) as Parcelable
            }
            parcel.recycle()
            dataReceiver!!.dataReceived(listPath, from, itemsSent)
        }
    }

    private inner class ListMessageResultCallback(private val listPath: String) :
        ResultCallback<SendMessageResult> {
        override fun onResult(sendMessageResult: SendMessageResult) {
            if (!sendMessageResult.status.isSuccess) {
                if (dataReceiver != null) dataReceiver!!.onError(
                    listPath,
                    CommonPlayServicesPaths.getErrorCodeFromGMSStatus(sendMessageResult.status.statusCode)
                )
            }
        }
    }
}
