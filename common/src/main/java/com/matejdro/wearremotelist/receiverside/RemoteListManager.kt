package com.matejdro.wearremotelist.receiverside;

import android.os.Parcelable;
import android.util.ArrayMap;

import androidx.annotation.NonNull;

import com.matejdro.wearremotelist.ErrorListener;
import com.matejdro.wearremotelist.receiverside.conn.ConnectionToProvider;

import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;

public class RemoteListManager
{
    private ConnectionToProvider connectionToProvider;
    private ConnectionToProvider originalConnectionToProvider;

    private ListDataReceiver listDataReceiver;
    private RemoteListListener remoteListListener;

    private volatile boolean transferring = false;
    private PriorityBlockingQueue<RemoteListRequest> transfers = new PriorityBlockingQueue<>();

    private Map<String, RemoteListImpl<?>> activeLists = new ArrayMap<>();

    public RemoteListManager(ConnectionToProvider connectionToProvider, RemoteListListener listener)
    {
        this.originalConnectionToProvider = connectionToProvider;
        this.connectionToProvider = new ProxyConnectionToProvider(connectionToProvider);
        this.remoteListListener = listener;

        this.listDataReceiver = new MainDataReceiver();
        this.connectionToProvider.setDataReceiver(listDataReceiver);
    }

    public void setListener(RemoteListListener remoteListListener)
    {
        this.remoteListListener = remoteListListener;
    }

    /**
     * Get existing list that has already been created.
     * @param listPath Path of the list.
     * @return List or {@code null} if list with such path has not yet been created
     */
    @SuppressWarnings("unchecked")
    public <T extends Parcelable> RemoteList<T> getExistingList(String listPath)
    {
        return (RemoteList<T>) activeLists.get(listPath);
    }

    /**
     * Create new remote list. If list with same path was already created before, old existing list object will not be active anymore and new one will be returned to you.
     * @param listPath Path of the list. Provider side will receive this string to identify the list.
     * @param creator Creator that can unparcel items from this list.
     * @param itemStorageCapacity Amount of items that can be cached on the device at the same time. Use lower numbers if items are big and receiver device has low amount of RAM (such as smartwatch).
     * @param amountToRequestAtOnce Amount of items that can be transferred in one message. Should be several times lower than {@code itemStorageCapacity}. Must be at least 3.
     * @param <T> Type of the object that this list stores.
     */
    public <T extends Parcelable> RemoteList<T> createRemoteList(String listPath, Parcelable.Creator<T> creator, int itemStorageCapacity, int amountToRequestAtOnce)
    {
        if (amountToRequestAtOnce < 3)
            throw new IllegalArgumentException("amountToRequestAtOnce must be at least 3.");

        if (itemStorageCapacity <= amountToRequestAtOnce)
            throw new IllegalArgumentException("itemStorageCapacity must be bigger than amountToRequestAtOnce.");

        RemoteList<T> list = new RemoteListImpl<T>(listPath, creator, connectionToProvider, itemStorageCapacity, (amountToRequestAtOnce - 1) / 2);
        activeLists.put(listPath, (RemoteListImpl<?>) list);
        list.invalidate();

        return list;
    }

    private class MainDataReceiver implements ListDataReceiver
    {
        @Override
        public void updateSizeReceived(String listPath, int newSize)
        {
            RemoteListImpl<?> list = activeLists.get(listPath);
            if (list == null)
                return;

            list.updateSizeReceived(listPath, newSize);
            remoteListListener.onListSizeChanged(listPath);
        }

        @Override
        public void dataReceived(String listPath, int from, Parcelable[] data)
        {
            RemoteListImpl<?> list = activeLists.get(listPath);
            if (list == null)
                return;

            list.dataReceived(listPath, from, data);
            remoteListListener.newEntriesTransferred(listPath, from, from + data.length - 1);

            transferring = false;

            RemoteListRequest nextTransfer = transfers.poll();
            if (nextTransfer != null)
            {

                originalConnectionToProvider.requestItems(nextTransfer.getList().getPath(), nextTransfer.getFrom(), nextTransfer.getTo());
                transferring = true;
            }

        }

        @Override
        public Parcelable.Creator getParcelableCreator(String listPath)
        {
            RemoteListImpl<?> list = activeLists.get(listPath);
            if (list == null)
                return null;

            return list.getParcelableCreator(listPath);
        }

        @Override
        public void onError(String listPath, int errorCode)
        {
            RemoteListImpl<?> list = activeLists.get(listPath);
            if (list == null)
                return;

            list.onError(listPath, errorCode);
            remoteListListener.onError(listPath, errorCode);
        }
    }

    private class ProxyConnectionToProvider implements ConnectionToProvider
    {
        private ConnectionToProvider original;

        public ProxyConnectionToProvider(ConnectionToProvider original)
        {
            this.original = original;
        }

        @Override
        public void requestListSize(String listPath)
        {
            //This packet is small and can be simultaneous.
            original.requestListSize(listPath);
        }

        @Override
        public void requestItems(String listPath, int from, int to)
        {
            if (transfers.isEmpty() && !transferring)
            {
                original.requestItems(listPath, from, to);
                transferring = true;
            }
            else
            {
                //Each list can be entered only once in the queue.
                boolean existingRequestUpdated = false;

                for (RemoteListRequest remoteListRequest : transfers)
                {
                    if (remoteListRequest.getList().getPath().equals(listPath))
                    {
                        remoteListRequest.updateFromTo(from, to);
                        existingRequestUpdated = true;
                        break;
                    }
                }

                if (!existingRequestUpdated)
                    transfers.add(new RemoteListRequest(getExistingList(listPath), from, to));
            }

        }

        @Override
        public void setDataReceiver(ListDataReceiver receiver)
        {
            original.setDataReceiver(receiver);
        }
    }

    public interface ListDataReceiver extends ErrorListener
    {
        void updateSizeReceived(String listPath, int newSize);
        void dataReceived(String listPath, int from, Parcelable[] data);
        Parcelable.Creator getParcelableCreator(String listPath);
    }

    private static class RemoteListRequest implements Comparable<RemoteListRequest>
    {
        public RemoteListRequest(RemoteList list, int from, int to)
        {
            this.list = list;
            this.from = from;
            this.to = to;
        }

        private RemoteList list;
        private int from;
        private int to;

        public RemoteList getList()
        {
            return list;
        }

        public int getFrom()
        {
            return from;
        }

        public int getTo()
        {
            return to;
        }

        public void updateFromTo(int from, int to)
        {
            this.from = from;
            this.to = to;
        }

        @Override
        public int compareTo(@NonNull RemoteListRequest another)
        {
            return another.getList().getPriority() - this.getList().getPriority();
        }
    }
}
