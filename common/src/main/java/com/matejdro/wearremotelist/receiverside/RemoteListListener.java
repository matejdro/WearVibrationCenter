package com.matejdro.wearremotelist.receiverside;

import com.matejdro.wearremotelist.ErrorListener;

import java.util.List;

public interface RemoteListListener extends ErrorListener
{
    /**
     * Triggered when list provider changed the size of the list. (including when list is created).
     * When this method is executed, {@link List#size()} of the remote list already has proper size.
     * @param listPath Path of the list that changed.
     */
    void onListSizeChanged(String listPath);

    /**
     * Triggered after new entries are transferred from the list provider and added to the list.
     * Use this to refresh adapters that are linked to the list.
     * @param listPath Path of the list that received the entries
     * @param from Index of the first entry that was received.
     * @param to Index of the last entry that was received.
     */
    void newEntriesTransferred(String listPath, int from, int to);
}
