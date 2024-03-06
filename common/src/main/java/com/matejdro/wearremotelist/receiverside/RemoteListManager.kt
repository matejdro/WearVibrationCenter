package com.matejdro.wearremotelist.receiverside

import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.ArrayMap
import com.matejdro.wearremotelist.ErrorListener
import com.matejdro.wearremotelist.receiverside.conn.ConnectionToProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.PriorityBlockingQueue

class RemoteListManager(
    private val originalConnectionToProvider: ConnectionToProvider,
    listener: RemoteListListener
) {
    private val connectionToProvider: ConnectionToProvider
    private val listDataReceiver: ListDataReceiver
    private var remoteListListener: RemoteListListener

    @Volatile
    private var transferring = false
    private val transfers = PriorityBlockingQueue<RemoteListRequest>()
    private val activeLists: MutableMap<String, RemoteListImpl<*>> = ArrayMap()

    init {
        connectionToProvider = ProxyConnectionToProvider(originalConnectionToProvider)
        remoteListListener = listener
        listDataReceiver = MainDataReceiver()
        originalConnectionToProvider.setDataReceiver(listDataReceiver)
    }

    fun setListener(remoteListListener: RemoteListListener) {
        this.remoteListListener = remoteListListener
    }

    /**
     * Get existing list that has already been created.
     * @param listPath Path of the list.
     * @return List or `null` if list with such path has not yet been created
     */
    fun <T : Parcelable?> getExistingList(listPath: String): RemoteList<T> {
        return activeLists[listPath] as RemoteList<T>
    }

    /**
     * Create new remote list. If list with same path was already created before, old existing list object will not be active anymore and new one will be returned to you.
     * @param listPath Path of the list. Provider side will receive this string to identify the list.
     * @param creator Creator that can unparcel items from this list.
     * @param itemStorageCapacity Amount of items that can be cached on the device at the same time. Use lower numbers if items are big and receiver device has low amount of RAM (such as smartwatch).
     * @param amountToRequestAtOnce Amount of items that can be transferred in one message. Should be several times lower than `itemStorageCapacity`. Must be at least 3.
     * @param <T> Type of the object that this list stores.
    </T> */
    fun <T : Parcelable?> createRemoteList(
        listPath: String,
        creator: Creator<T>,
        itemStorageCapacity: Int,
        amountToRequestAtOnce: Int,
        coroutineScope: CoroutineScope
    ): RemoteList<T> {
        require(!(amountToRequestAtOnce < 3)) { "amountToRequestAtOnce must be at least 3." }
        if (itemStorageCapacity <= amountToRequestAtOnce) throw IllegalArgumentException("itemStorageCapacity must be bigger than amountToRequestAtOnce.")
        val list: RemoteList<T> = RemoteListImpl(
            listPath,
            creator,
            originalConnectionToProvider,
            coroutineScope,
            itemStorageCapacity,
            (amountToRequestAtOnce - 1) / 2,
        )
        activeLists[listPath] = list as RemoteListImpl<*>
        coroutineScope.launch {
            list.invalidate()
        }
        return list
    }

    private inner class MainDataReceiver : ListDataReceiver {
        override fun updateSizeReceived(listPath: String, newSize: Int) {
            val list = activeLists.get(listPath) ?: return
            list.updateSizeReceived(listPath, newSize)
            remoteListListener.onListSizeChanged(listPath)
        }

        override suspend fun dataReceived(listPath: String, from: Int, data: Array<Parcelable?>) {
            val list = activeLists.get(listPath) ?: return
            list.dataReceived(listPath, from, data)
            remoteListListener.newEntriesTransferred(listPath, from, from + data.size - 1)
            transferring = false
            val nextTransfer = transfers.poll()
            if (nextTransfer != null) {
                originalConnectionToProvider.requestItems(
                    nextTransfer.list.path,
                    nextTransfer.from,
                    nextTransfer.to
                )
                transferring = true
            }
        }

        override fun getParcelableCreator(listPath: String): Creator<*>? {
            val list = activeLists.get(listPath) ?: return null
            return list.getParcelableCreator(listPath)
        }

        override fun onError(listPath: String, errorCode: Int) {
            val list = activeLists.get(listPath) ?: return
            list.onError(listPath, errorCode)
            remoteListListener.onError(listPath, errorCode)
        }
    }

    private inner class ProxyConnectionToProvider(private val original: ConnectionToProvider) :
        ConnectionToProvider {
        override suspend fun requestListSize(listPath: String) {
            //This packet is small and can be simultaneous.
            original.requestListSize(listPath)
        }

        override suspend fun requestItems(listPath: String, from: Int, to: Int) {
            if (transfers.isEmpty() && !transferring) {
                original.requestItems(listPath, from, to)
                transferring = true
            } else {
                //Each list can be entered only once in the queue.
                var existingRequestUpdated = false
                for (remoteListRequest in transfers) {
                    if (remoteListRequest.list.path == listPath) {
                        remoteListRequest.updateFromTo(from, to)
                        existingRequestUpdated = true
                        break
                    }
                }
                if (!existingRequestUpdated) transfers.add(
                    RemoteListRequest(
                        getExistingList<Parcelable>(
                            listPath
                        ), from, to
                    )
                )
            }
        }

        override fun setDataReceiver(receiver: ListDataReceiver?) {
            original.setDataReceiver(receiver)
        }
    }

    interface ListDataReceiver : ErrorListener {
        fun updateSizeReceived(listPath: String, newSize: Int)
        suspend fun dataReceived(listPath: String, from: Int, data: Array<Parcelable?>)
        fun getParcelableCreator(listPath: String): Creator<*>?
    }

    private class RemoteListRequest(val list: RemoteList<*>, var from: Int, var to: Int) :
        Comparable<RemoteListRequest> {
        fun updateFromTo(from: Int, to: Int) {
            this.from = from
            this.to = to
        }

        override fun compareTo(another: RemoteListRequest): Int {
            return another.list.priority - this.list.priority
        }
    }
}
