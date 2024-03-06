package com.matejdro.wearremotelist.providerside.conn

import android.net.Uri
import android.os.Parcel
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.matejdro.wearremotelist.CommonPlayServicesPaths
import com.matejdro.wearremotelist.providerside.RemoteListProvider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer

class PlayServicesConnectionToReceiver(
    private val messageClient: MessageClient,
    registerListener: Boolean
) : ConnectionToReceiver, MessageClient.OnMessageReceivedListener {
    private var remoteListProvider: RemoteListProvider? = null

    /**
     * @param googleApiClient Connected GoogleApiClient with Wearable API enabled.
     * @param registerListener If `true`, this class will automatically register itself as message listener onto provided GoogleApiClient.
     * If `false`, you must manually call onMessageReceived() when new messages arrive.
     */
    init {
        if (registerListener) messageClient.addListener(this)
    }

    /**
     * Remove my listener from provided GoogleApiClient.
     */
    fun disconnect() {
        messageClient.removeListener(this)
    }

    override suspend fun updateListSize(listPath: String, nodeId: String) {
        checkNotNull(remoteListProvider) { "List Provider is still null when trying to send items." }
        val targetUri = CommonPlayServicesPaths.RESPONSE_LIST_SIZE_URI
            .buildUpon()
            .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
            .build()
        val size = remoteListProvider!!.getRemoteListSize(listPath)
        val data = ByteBuffer.allocate(4).putInt(size).array()


        messageClient.sendMessage(nodeId, targetUri.toString(), data).await()
    }

    override suspend fun sendItem(listPath: String, position: Int, nodeId: String) {
        sendItems(listPath, position, position, nodeId)
    }

    override suspend fun sendItems(listPath: String, from: Int, to: Int, nodeId: String) {
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
        messageClient.sendMessage(nodeId, targetUri.toString(), data).await()
    }

    override fun setProvider(provider: RemoteListProvider?) {
        remoteListProvider = provider
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val messageUri = Uri.parse(messageEvent.path)
        if (CommonPlayServicesPaths.REQUEST_LIST_SIZE_URI.path == messageUri.path) {
            val listPath =
                messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH) ?: return
            GlobalScope.launch(Dispatchers.Main.immediate) {
                updateListSize(listPath, messageEvent.sourceNodeId)
            }
        } else if (CommonPlayServicesPaths.REQUEST_ITEMS_URI.path == messageUri.path) {
            val listPath =
                messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH) ?: return
            val from = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_FROM)!!
                .toInt()
            val to = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO)!!
                .toInt()
            GlobalScope.launch(Dispatchers.Main.immediate) {
                sendItems(listPath, from, to, messageEvent.sourceNodeId)
            }
        }
    }
}
