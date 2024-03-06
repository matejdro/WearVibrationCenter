package com.matejdro.wearremotelist.providerside.conn

import com.matejdro.wearremotelist.providerside.RemoteListProvider

interface ConnectionToReceiver {
    /**
     * Manually update list size on the watch.
     * ([RemoteListProvider.getRemoteListSize] on linked provider will be called automatically after this method is ran.)
     * @param listPath Path of the list to update list size on.
     * @param nodeId ID of the node that will receive the new size.
     */
    suspend fun updateListSize(listPath: String, nodeId: String)

    /**
     * Manually (re-)send specific range of items to the receiver
     * ([RemoteListProvider.getItem] on linked provider will be called automatically for every item in range after this method is ran.).
     * Note that this method does NOT check the list bounds.
     * @param listPath Path of the list to send items to.
     * @param from First item of the updating range.
     * @param to Last item of the updating range.
     * @param nodeId ID of the node that will receive the new size.
     */
    suspend fun sendItems(listPath: String, from: Int, to: Int, nodeId: String)

    /**
     * Manually (re-)send specific item to the receiver.
     * ([RemoteListProvider.getItem] on linked provider will be called automatically after this method is ran.)
     * Note that this method does NOT check the list bounds.
     * @param listPath Path of the list to send items to.
     * @param position Position of the item to re-send
     * @param nodeId ID of the node that will receive the new size.
     */
    suspend fun sendItem(listPath: String, position: Int, nodeId: String)

    /**
     * Set provider that will provide list contents.
     */
    fun setProvider(provider: RemoteListProvider?)
}
