package com.matejdro.wearremotelist;

import android.os.Parcelable;

import androidx.annotation.IntDef;

import com.matejdro.wearremotelist.receiverside.RemoteListManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface ErrorListener
{
    /**
     * Indicates that provided did not recognize sent list path.
     */
    int ERROR_UNKNOWN_LIST = 0;

    /**
     * Indicates that transfer message was too large. Try reducing {@code amountToRequestAtOnce} when {@link RemoteListManager#createRemoteList(String, Parcelable.Creator, int, int) creating remote list}.
     */
    int ERROR_DATA_ITEM_TOO_LARGE = 1;

    /**
     * Indicates that message could not physically get to the other side.
     */
    int ERROR_DISCONNECTED = 2;

    int ERROR_UNKNOWN = 2;

    @IntDef({ERROR_UNKNOWN_LIST, ERROR_DATA_ITEM_TOO_LARGE, ERROR_DISCONNECTED, ERROR_UNKNOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TransferError {}

    /**
     * Triggered after list entries could not be transferred.
     * @param listPath Path of the list that was involved. Can be {@code null} if this is unknown.
     */
    void onError(String listPath, @TransferError int errorCode);
}
