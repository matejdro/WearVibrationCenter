package com.matejdro.wearremotelist.providerside.conn;

import android.os.Bundle;

import androidx.annotation.CallSuper;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.matejdro.wearremotelist.providerside.RemoteListProvider;

public abstract class ListWearableListenerService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, RemoteListProvider
{
    private GoogleApiClient googleApiClient;
    private PlayServicesConnectionToReceiver connectionToReceiver;

    @Override
    @CallSuper
    public void onCreate()
    {
        super.onCreate();

        googleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(this).build();
        googleApiClient.connect();
    }

    public GoogleApiClient getGoogleApiClient()
    {
        return googleApiClient;
    }

    public PlayServicesConnectionToReceiver getConnectionToReceiver()
    {
        return connectionToReceiver;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    @CallSuper
    public void onMessageReceived(MessageEvent messageEvent)
    {
        if (!googleApiClient.isConnected())
            googleApiClient.blockingConnect();

        connectionToReceiver.onMessageReceived(messageEvent);
    }

    @Override
    @CallSuper
    public void onConnected(Bundle bundle)
    {
        connectionToReceiver = new PlayServicesConnectionToReceiver(googleApiClient, false);
        connectionToReceiver.setProvider(this);
    }

    @Override
    public void onConnectionSuspended(int i)
    {
    }
}
