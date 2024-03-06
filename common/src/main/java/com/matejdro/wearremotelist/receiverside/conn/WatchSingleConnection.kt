package com.matejdro.wearremotelist.receiverside.conn

import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import kotlinx.coroutines.tasks.await
import java.util.Collections

class WatchSingleConnection(messageClient: MessageClient, private val nodeClient: NodeClient) :
    PlayServicesConnectionToProvider(messageClient) {

    override suspend fun getProviderNodeId(): String? {
        val connectedNodes = nodeClient.getConnectedNodes().await()
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
