package com.matejdro.wearremotelist.receiverside

import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.LruCache
import com.matejdro.wearremotelist.ErrorListener.TransferError
import com.matejdro.wearremotelist.receiverside.RemoteListManager.ListDataReceiver
import com.matejdro.wearremotelist.receiverside.conn.ConnectionToProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

internal class RemoteListImpl<E : Parcelable?>(
    override val path: String,
    private val creator: Creator<E>,
    private val connectionToProvider: ConnectionToProvider,
    private val coroutineScope: CoroutineScope,
    itemStorageSize: Int,
    private val itemRetrievalRangeInOneDirection: Int
) : RemoteList<E>, ListDataReceiver {
    private val entries: LruCache<Int, E>
    private var size = 0
    override var priority = 0

    init {
        entries = LruCache(itemStorageSize)
    }

    override fun get(position: Int): E {
        if (position < 0 || position >= size()) throw ArrayIndexOutOfBoundsException()
        val entry = entries[position]
        if (entry == null) {
            coroutineScope.launch {

                //Only send one packet at a time to prevent connection clogging when there are many requests at once (for example when scrolling)
                loadItems(position, false)
            }
        }
        return entry
    }

    override fun size(): Int {
        return size
    }

    override fun isLoaded(position: Int): Boolean {
        if (position < 0 || position >= size()) throw ArrayIndexOutOfBoundsException()
        return entries[position] != null
    }

    override suspend fun loadItems(position: Int, exact: Boolean) {
        loadItems(position, itemRetrievalRangeInOneDirection, exact)
    }

    private suspend fun loadItems(position: Int, amountIntoEveryDirection: Int, exact: Boolean) {
        if (position < 0 || position >= size()) throw ArrayIndexOutOfBoundsException()
        var newPosition = position
        if (!exact) {
            //There may be items already in this range,
            //so we shift range out of known items to
            //maximize efficiency
            for (i in -amountIntoEveryDirection..0) {
                val absoluteLocation = i + position
                if (absoluteLocation >= 0 && entries[absoluteLocation] == null) {
                    val amountToMove = amountIntoEveryDirection + i
                    newPosition = position + amountToMove
                    break
                }
            }
            if (newPosition == position) {
                for (i in amountIntoEveryDirection downTo 0) {
                    val absoluteLocation = i + position
                    if (absoluteLocation < size && entries[absoluteLocation] == null) {
                        val amountToMove = amountIntoEveryDirection - i
                        newPosition = position - amountToMove
                        break
                    }
                }
            }
        }
        val from = max(0.0, (newPosition - amountIntoEveryDirection).toDouble()).toInt()
        val to = min((size - 1).toDouble(), (newPosition + amountIntoEveryDirection).toDouble())
            .toInt()
        connectionToProvider.requestItems(path, from, to)
    }

    override suspend fun fillAround(position: Int, amountIntoEveryDirection: Int) {
        val from = max(0.0, (position - amountIntoEveryDirection).toDouble()).toInt()
        val to = min((size - 1).toDouble(), (position + amountIntoEveryDirection).toDouble())
            .toInt()
        var allLoaded = true
        for (i in from..to) {
            if (!isLoaded(i)) {
                allLoaded = false
                break
            }
        }
        if (allLoaded) return
        loadItems(position, amountIntoEveryDirection, false)
    }

    override suspend fun invalidate() {
        size = 0
        entries.evictAll()
        connectionToProvider.requestListSize(path)
    }

    override fun updateSizeReceived(listPath: String, newSize: Int) {
        if (newSize == 0) {
            entries.evictAll()
        } else if (size() > newSize) {
            for (key in entries.snapshot().keys) {
                if (key < newSize) entries.remove(key)
            }
        }
        size = newSize
    }

    override suspend fun dataReceived(listPath: String, from: Int, data: Array<Parcelable?>) {
        for (i in data.indices) {
            entries.put(i + from, data[i] as E)
        }
    }

    override fun getParcelableCreator(listPath: String): Creator<*> {
        return creator
    }

    override fun onError(listPath: String, @TransferError errorCode: Int) {}
}
