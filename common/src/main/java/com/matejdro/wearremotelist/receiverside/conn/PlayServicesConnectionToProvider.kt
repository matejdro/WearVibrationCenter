package com.matejdro.wearremotelist.receiverside.conn;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.matejdro.wearremotelist.CommonPlayServicesPaths;
import com.matejdro.wearremotelist.ErrorListener;
import com.matejdro.wearremotelist.receiverside.RemoteListManager;

import java.nio.ByteBuffer;

/**
 * Class that connects to the provider via Google Play Services's MessageApi.
 */
public abstract class PlayServicesConnectionToProvider implements ConnectionToProvider, MessageApi.MessageListener
{
    private GoogleApiClient googleApiClient;
    private RemoteListManager.ListDataReceiver dataReceiver;

    /**
     * @param googleApiClient Connected GoogleApiClient with Wearable API enabled.
     */
    public PlayServicesConnectionToProvider(GoogleApiClient googleApiClient)
    {
        if (googleApiClient == null)
            throw new NullPointerException();

        if (!googleApiClient.isConnected())
            throw new IllegalArgumentException("Provided GoogleApiClient is not connected to Google Play Services.");
        if (!googleApiClient.hasConnectedApi(Wearable.API))
            throw new IllegalArgumentException("Provided GoogleApiClient does not have Wearable API connected.");

        this.googleApiClient = googleApiClient;

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
    public void setDataReceiver(RemoteListManager.ListDataReceiver dataReceiver)
    {
        this.dataReceiver = dataReceiver;
    }

    public GoogleApiClient getGoogleApiClient()
    {
        return googleApiClient;
    }

    public abstract @Nullable String getProviderNodeId();

    @Override
    public void requestListSize(final String listPath)
    {
        if (dataReceiver == null)
            throw new IllegalStateException("Data Receiver is still null when transferring messages");

        new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... voids)
            {
                String providerNodeId = getProviderNodeId();
                if (providerNodeId == null)
                {
                    dataReceiver.onError(listPath, ErrorListener.ERROR_DISCONNECTED);
                    return null;
                }

                Uri targetUri = CommonPlayServicesPaths.REQUEST_LIST_SIZE_URI
                        .buildUpon()
                        .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
                        .build();

                Wearable.MessageApi.sendMessage(googleApiClient, providerNodeId, targetUri.toString(), null)
                        .setResultCallback(new ListMessageResultCallback(listPath));

                return null;
            }
        }.execute((Void[]) null);
    }

    @Override
    public void requestItems(final String listPath, final int from, final int to)
    {
        if (dataReceiver == null)
            throw new IllegalStateException("Data Receiver is still null when transferring messages");

        new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... voids)
            {
                String providerNodeId = getProviderNodeId();
                if (providerNodeId == null)
                {
                    dataReceiver.onError(listPath, ErrorListener.ERROR_DISCONNECTED);
                    return null;
                }

                Uri targetUri = CommonPlayServicesPaths.REQUEST_ITEMS_URI
                        .buildUpon()
                        .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH, listPath)
                        .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_FROM, Integer.toString(from))
                        .appendQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO, Integer.toString(to))

                        .build();

                Wearable.MessageApi.sendMessage(googleApiClient, providerNodeId, targetUri.toString(), null)
                        .setResultCallback(new ListMessageResultCallback(listPath));

                return null;
            }
        }.execute((Void[]) null);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        if (dataReceiver == null)
            throw new IllegalStateException("Data Receiver is still null when messages are arriving.");

        Uri messageUri = Uri.parse(messageEvent.getPath());
        if (CommonPlayServicesPaths.RESPONSE_LIST_SIZE_URI.getPath().equals(messageUri.getPath()))
        {

            String listPath = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH);
            int newSize = ByteBuffer.wrap(messageEvent.getData()).getInt();

            if (newSize < 0)
            {
                dataReceiver.onError(listPath, ErrorListener.ERROR_UNKNOWN_LIST);
            }
            else
            {
                dataReceiver.updateSizeReceived(listPath, newSize);
            }
        }
        else if (CommonPlayServicesPaths.RESPONSE_ITEMS_URI.getPath().equals(messageUri.getPath()))
        {
            String listPath = messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_LIST_PATH);
            int from = Integer.parseInt(messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_FROM));
            int to = Integer.parseInt(messageUri.getQueryParameter(CommonPlayServicesPaths.PARAMETER_ITEMS_TO));

            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(messageEvent.getData(), 0, messageEvent.getData().length);
            parcel.setDataPosition(0);

            Parcelable.Creator creator = dataReceiver.getParcelableCreator(listPath);
            Parcelable[] itemsSent = new Parcelable[to - from + 1];
            for (int i = 0; i < itemsSent.length; i++)
            {
                itemsSent[i] = (Parcelable) creator.createFromParcel(parcel);
            }

            parcel.recycle();
            dataReceiver.dataReceived(listPath, from, itemsSent);
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
                if (dataReceiver != null)
                    dataReceiver.onError(listPath, CommonPlayServicesPaths.getErrorCodeFromGMSStatus(sendMessageResult.getStatus().getStatusCode()));
            }
        }
    }
}
