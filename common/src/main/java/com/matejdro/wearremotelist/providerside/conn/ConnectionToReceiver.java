package com.matejdro.wearremotelist.providerside.conn;

import com.matejdro.wearremotelist.providerside.RemoteListProvider;

public interface ConnectionToReceiver
{
    /**
     * Manually update list size on the watch.
     * ({@link RemoteListProvider#getRemoteListSize(String)} on linked provider will be called automatically after this method is ran.)
     * @param listPath Path of the list to update list size on.
     * @param nodeId ID of the node that will receive the new size.
     */
    void updateListSize(String listPath, String nodeId);

    /**
     * Manually (re-)send specific range of items to the receiver
     * ({@link RemoteListProvider#getItem(String, int)} on linked provider will be called automatically for every item in range after this method is ran.).
     * Note that this method does NOT check the list bounds.
     * @param listPath Path of the list to send items to.
     * @param from First item of the updating range.
     * @param to Last item of the updating range.
     * @param nodeId ID of the node that will receive the new size.
     */
    void sendItems(String listPath, int from, int to, String nodeId);

    /**
     * Manually (re-)send specific item to the receiver.
     * ({@link RemoteListProvider#getItem(String, int)} on linked provider will be called automatically after this method is ran.)
     * Note that this method does NOT check the list bounds.
     * @param listPath Path of the list to send items to.
     * @param position Position of the item to re-send
     * @param nodeId ID of the node that will receive the new size.
     */
    void sendItem(String listPath, int position, String nodeId);


    /**
     * Set provider that will provide list contents.
     */
    void setProvider(RemoteListProvider provider);
}
