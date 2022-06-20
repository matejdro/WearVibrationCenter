package com.matejdro.wearremotelist.providerside.conn;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearremotelist.CommonPlayServicesPaths;
import com.matejdro.wearremotelist.providerside.RemoteListProvider;

import java.nio.ByteBuffer;

public class PlayServicesConnectionToReceiver implements ConnectionToReceiver, MessageApi.MessageListener
{
    private GoogleApiClient googleApiClient;
    private RemoteListProvider remoteListProvider;

    /**
     * @param googleApiClient Connected GoogleApiClient with Wearable API enabled.
     * @param registerListener If {@code true}, this class will automatically register itself as message listener onto provided GoogleApiClient.
     *                         If {@code false}, you must manually call onMessageReceived() when new messages arrive.
     */

    public PlayServicesConnectionToReceiver(GoogleApiClient googleApiClient, boolean registerListener)
    {
        if (googleApiClient == null)
            throw new NullPointerException();

        if (!googleApiClient.isConnected())
            throw new IllegalArgumentException("Provided GoogleApiClient is not connected to Google Play Services.");
        if (!googleApiClient.hasConnectedApi(Wearable.API))
            throw new IllegalArgumentException("Provided GoogleApiClient does not have Wearable API connected.");

        this.googleApiClient = googleApiClient;
        if (registerListener)
            Wearable.MessageApi.addListener(googleApiClient, this);
    }

    /**
     * Remove my listener from provided GoogleApiClient.
     */
    public void disconnect()
    {
        Wearable.MessageApi.removeListener(googleApiClient, this);
    }


    @Override
    public void updateListSize(String listPath, String nodeId)
    {
        if (remoteListProvider == null)
            throw new IllegalStateException("List Provider is still null when trying to send items.");

        Uri targetUri = CommonPlayServicesPaths.RESPONSE_LIST_SIZE_URI
                .buildUpon()
                .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
                .build();

        int size = remoteListProvider.getRemoteListSize(listPath);
        byte[] data = ByteBuffer.allocate(4).putInt(size).array();

        Wearable.MessageApi.sendMessage(googleApiClient, nodeId, targetUri.toString(), data)
                .setResultCallback(new ListMessageResultCallback(listPath));

    }

    @Override
    public void sendItem(String listPath, int position, String nodeId)
    {
        sendItems(listPath, position, position, nodeId);
    }

    @Override
    public void sendItems(String listPath, int from, int to, String nodeId)
    {
        if (remoteListProvider == null)
            throw new IllegalStateException("List Provider is still null when trying to send items.");

        Uri targetUri = CommonPlayServicesPaths.RESPONSE_ITEMS_URI
                .buildUpon()
                .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
                .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_FROM, Integer.toString(from))
                .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO, Integer.toString(to))
                .build();

        Parcel outParcel = Parcel.obtain();
        for (int i = from; i <= to; i++)
        {
            Parcelable item = remoteListProvider.getItem(listPath, i);
            item.writeToParcel(outParcel, 0);
        }

        byte[] data = outParcel.marshall();
        outParcel.recycle();

        Wearable.MessageApi.sendMessage(googleApiClient, nodeId, targetUri.toString(), data)
                .setResultCallback(new ListMessageResultCallback(listPath));
    }

    @Override
    public void setProvider(RemoteListProvider provider)
    {
        this.remoteListProvider = provider;
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        Uri messageUri = Uri.parse(messageEvent.getPath());

        if (CommonPlayServicesPaths.REQUEST_LIST_SIZE_URI.getPath().equals(messageUri.getPath()))
        {
            String listPath = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH);
            updateListSize(listPath, messageEvent.getSourceNodeId());
        }
        else if (CommonPlayServicesPaths.REQUEST_ITEMS_URI.getPath().equals(messageUri.getPath()))
        {
            String listPath = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH);
            int from = Integer.parseInt(messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_FROM));
            int to = Integer.parseInt(messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO));

            sendItems(listPath, from, to, messageEvent.getSourceNodeId());
        }
    }

    private class ListMessageResultCallback implements ResultCallback<MessageApi.SendMessageResult>
    {
        private String listPath;

        public ListMessageResultCallback(String listPath)
        {
            this.listPath = listPath;
        }

        @Override
        public void onResult(MessageApi.SendMessageResult sendMessageResult)
        {
            if (!sendMessageResult.getStatus().isSuccess())
            {
                if (remoteListProvider != null)
                    remoteListProvider.onError(listPath, CommonPlayServicesPaths.getErrorCodeFromGMSStatus(sendMessageResult.getStatus().getStatusCode()));
            }
        }
    }

}
