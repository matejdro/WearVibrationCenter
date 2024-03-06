package com.matejdro.wearremotelist.receiverside.conn

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.matejdro.wearremotelist.CommonPlayServicesPaths
import com.matejdro.wearremotelist.ErrorListener
import com.matejdro.wearremotelist.receiverside.RemoteListManager.ListDataReceiver
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer

/**
 * Class that connects to the provider via Google Play Services's MessageApi.
 */
abstract class PlayServicesConnectionToProvider(private val messageClient: MessageClient) :
    ConnectionToProvider, MessageClient.OnMessageReceivedListener {
    private var dataReceiver: ListDataReceiver? = null

    /**
     * @param googleApiClient Connected GoogleApiClient with Wearable API enabled.
     */
    init {
        messageClient.addListener(this)
    }

    /**
     * Remove my listener from provided GoogleApiClient.
     */
    fun disconnect() {
        messageClient.removeListener(this)
    }

    override fun setDataReceiver(receiver: ListDataReceiver?) {
        this.dataReceiver = receiver
    }

    abstract suspend fun getProviderNodeId(): String?
    override suspend fun requestListSize(listPath: String) {
        val dataReceiver = checkNotNull(dataReceiver) { "Data Receiver is still null when transferring messages" }

        val providerNodeId = getProviderNodeId()
        if (providerNodeId == null) {
            dataReceiver.onError(listPath, ErrorListener.ERROR_DISCONNECTED)
            return
        }
        val targetUri = CommonPlayServicesPaths.REQUEST_LIST_SIZE_URI
            .buildUpon()
            .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
            .build()

        messageClient.sendMessage(
            providerNodeId,
            targetUri.toString(),
            byteArrayOf()
        ).await()
    }

    override suspend fun requestItems(listPath: String, from: Int, to: Int) {
        val dataReceiver = checkNotNull(dataReceiver) { "Data Receiver is still null when transferring messages" }

        val providerNodeId = getProviderNodeId()
        if (providerNodeId == null) {
            dataReceiver.onError(listPath, ErrorListener.ERROR_DISCONNECTED)
            return
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
        messageClient.sendMessage(
            providerNodeId,
            targetUri.toString(),
            byteArrayOf()
        ).await()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val dataReceiver = checkNotNull(dataReceiver) { "Data Receiver is still null when messages are arriving." }

        GlobalScope.launch(Dispatchers.Main.immediate) {
            val messageUri = Uri.parse(messageEvent.path)
            if (CommonPlayServicesPaths.RESPONSE_LIST_SIZE_URI.path == messageUri.path) {
                val listPath =
                    messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH) ?: return@launch
                val newSize = ByteBuffer.wrap(messageEvent.data).getInt()
                if (newSize < 0) {
                    dataReceiver.onError(listPath, ErrorListener.ERROR_UNKNOWN_LIST)
                } else {
                    dataReceiver.updateSizeReceived(listPath, newSize)
                }
            } else if (CommonPlayServicesPaths.RESPONSE_ITEMS_URI.path == messageUri.path) {
                val listPath =
                    messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH) ?: return@launch
                val from =
                    messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_FROM)
                        ?.toInt() ?: return@launch
                val to = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO)
                    ?.toInt() ?: return@launch
                val parcel = Parcel.obtain()
                parcel.unmarshall(messageEvent.data, 0, messageEvent.data.size)
                parcel.setDataPosition(0)
                val creator = dataReceiver.getParcelableCreator(listPath) ?: return@launch
                val itemsSent = arrayOfNulls<Parcelable>(to - from + 1)
                for (i in itemsSent.indices) {
                    itemsSent[i] = creator.createFromParcel(parcel) as Parcelable
                }
                parcel.recycle()
                dataReceiver.dataReceived(listPath, from, itemsSent)

            }
        }
    }
}
