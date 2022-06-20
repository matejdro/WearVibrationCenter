package com.matejdro.wearremotelist.providerside;

import android.os.Parcelable;

import com.matejdro.wearremotelist.ErrorListener;

public interface RemoteListProvider extends ErrorListener
{
    /**
     * Callback that returns number of items in specified list.
     * @param listPath Path of the list that changed.
     * @return number of items in the list or {@code -1} if you do not recognize list with specified path
     */
    int getRemoteListSize(String listPath);

    /**
     * Callback that returns item from specific list
     * @param listPath Path of the list that changed.
     * @return List item at specified position. {@code null} is not allowed.
     */
    Parcelable getItem(String listPath, int position);
}
