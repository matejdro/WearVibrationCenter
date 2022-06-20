package com.matejdro.wearremotelist.receiverside.conn;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WatchSingleConnection extends PlayServicesConnectionToProvider
{
    /**
     * {@inheritDoc}
     */
    public WatchSingleConnection(GoogleApiClient googleApiClient)
    {
        super(googleApiClient);
    }

    @Override
    public String getProviderNodeId()
    {
        List<Node> connectedNodes = Wearable.NodeApi.getConnectedNodes(getGoogleApiClient()).await().getNodes();
        if (connectedNodes == null || connectedNodes.isEmpty())
            return null;

        Collections.sort(connectedNodes, NodeNearbyComparator.INSTANCE);
        return connectedNodes.get(0).getId();
    }

    private static class NodeNearbyComparator implements Comparator<Node>
    {
        public static final NodeNearbyComparator INSTANCE = new NodeNearbyComparator();

        @Override
        public int compare(Node a, Node b)
        {
            int nearbyA = a.isNearby() ? 1 : 0;
            int nearbyB = b.isNearby() ? 1 : 0;
            return nearbyB - nearbyA;
        }
    }
}
