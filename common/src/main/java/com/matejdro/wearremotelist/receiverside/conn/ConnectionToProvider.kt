package com.matejdro.wearremotelist.receiverside.conn

import com.matejdro.wearremotelist.receiverside.RemoteListManager.ListDataReceiver

interface ConnectionToProvider {
    /**
     * Request list size update.
     * This is generally only used when list is created as provider is supposed to automatically send update when size changes.
     *
     * After update is received, [com.matejdro.wearremotelist.receiverside.RemoteListManager.ListDataReceiver.updateSizeReceived] will be called automatically.
     *
     * @param listPath Path of the list to update.
     */
    suspend fun requestListSize(listPath: String)

    /**
     * Request update of specific range of items.
     *
     * After update is received, [com.matejdro.wearremotelist.receiverside.RemoteListManager.ListDataReceiver.dataReceived]} will be called automatically.
     *
     * @param listPath Path of the list to update.
     * @param from First item in the range
     * @param to Last item in the range
     */
    suspend fun requestItems(listPath: String, from: Int, to: Int)

    /**
     * Set data receiver that receives data from the provider.
     */
    fun setDataReceiver(receiver: ListDataReceiver?)
}
