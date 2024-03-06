package com.matejdro.wearremotelist.receiverside.conn;

import android.os.Parcelable;

import com.matejdro.wearremotelist.receiverside.RemoteListManager;

public interface ConnectionToProvider
{
    /**
     * Request list size update.
     * This is generally only used when list is created as provider is supposed to automatically send update when size changes.
     *
     * After update is received, {@link com.matejdro.wearremotelist.receiverside.RemoteListManager.ListDataReceiver#updateSizeReceived(String, int)} will be called automatically.
     *
     * @param listPath Path of the list to update.
     */
    void requestListSize(String listPath);

    /**
     * Request update of specific range of items.
     *
     * After update is received, {@link com.matejdro.wearremotelist.receiverside.RemoteListManager.ListDataReceiver#dataReceived(String, int, Parcelable[])}} will be called automatically.
     *
     * @param listPath Path of the list to update.
     * @param from First item in the range
     * @param to Last item in the range
     */
    void requestItems(String listPath, int from, int to);

    /**
     * Set data receiver that receives data from the provider.
     */
    void setDataReceiver(RemoteListManager.ListDataReceiver receiver);
}
