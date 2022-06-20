package com.matejdro.wearremotelist;


import android.net.Uri;

import com.google.android.gms.wearable.WearableStatusCodes;

public class CommonPlayServicesPaths
{
    public static final String PARAMETER_LIST_PATH = "listPath";
    public static final String PARAMETER_ITEMS_FROM = "itemsFrom";
    public static final String PARAMETER_ITEMS_TO = "itemsTo";

    public static final Uri REQUEST_LIST_SIZE_URI = Uri.parse("/WearRemoteList/RequestSize");
    public static final Uri REQUEST_ITEMS_URI = Uri.parse("/WearRemoteList/RequestItems");

    public static final Uri RESPONSE_LIST_SIZE_URI = Uri.parse("/WearRemoteList/ResponseSize");
    public static final Uri RESPONSE_ITEMS_URI = Uri.parse("/WearRemoteList/ResponseItems");

    public static @ErrorListener.TransferError int getErrorCodeFromGMSStatus(int gmsStatusCode)
    {
        if (gmsStatusCode == WearableStatusCodes.DATA_ITEM_TOO_LARGE)
            return ErrorListener.ERROR_DATA_ITEM_TOO_LARGE;
        else if (gmsStatusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED || gmsStatusCode == WearableStatusCodes.INVALID_TARGET_NODE)
            return ErrorListener.ERROR_DISCONNECTED;
        else
            return ErrorListener.ERROR_UNKNOWN;
    }
}
