package com.matejdro.wearremotelist.receiverside.conn

import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import java.util.Collections

class WatchSingleConnection
/**
 * {@inheritDoc}
 */
    (googleApiClient: GoogleApiClient?) : PlayServicesConnectionToProvider(googleApiClient) {

    override fun getProviderNodeId(): String? {
        val connectedNodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await().nodes
        if (connectedNodes.isEmpty()) return null
        Collections.sort(connectedNodes, NodeNearbyComparator.INSTANCE)
        return connectedNodes[0].id
    }

    private class NodeNearbyComparator : Comparator<Node> {
        override fun compare(a: Node, b: Node): Int {
            val nearbyA = if (a.isNearby) 1 else 0
            val nearbyB = if (b.isNearby) 1 else 0
            return nearbyB - nearbyA
        }

        companion object {
            val INSTANCE = NodeNearbyComparator()
        }
    }
}
