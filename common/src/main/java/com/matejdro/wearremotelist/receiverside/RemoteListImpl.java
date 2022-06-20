package com.matejdro.wearremotelist.receiverside;

import android.os.Parcelable;
import android.util.LruCache;

import com.matejdro.wearremotelist.receiverside.conn.ConnectionToProvider;

class RemoteListImpl<E extends Parcelable> implements RemoteList<E>, RemoteListManager.ListDataReceiver
{
    private String                listPath;
    private LruCache<Integer, E>  entries;
    private Parcelable.Creator<E> creator;
    private ConnectionToProvider connectionToProvider;
    private int itemRetrievalRangeInOneDirection;

    private int size = 0;
    private int priority = 0;

    public RemoteListImpl(String listPath, Parcelable.Creator<E> creator, ConnectionToProvider connectionToProvider, int itemStorageSize, int itemRetrievalRangeInOneDirection)
    {
        this.listPath = listPath;
        this.connectionToProvider = connectionToProvider;
        this.itemRetrievalRangeInOneDirection = itemRetrievalRangeInOneDirection;
        this.creator = creator;

        entries = new LruCache<>(itemStorageSize);
    }

    @Override
    public String getPath()
    {
        return listPath;
    }

    @Override
    public E get(int position)
    {
        if (position < 0 || position >= size())
            throw new ArrayIndexOutOfBoundsException();

        E entry = entries.get(position);
        if (entry == null)
        {
            //Only send one packet at a time to prevent connection clogging when there are many requests at once (for example when scrolling)

            loadItems(position, false);
        }

        return entry;
    }

    @Override
    public int size()
    {
        return size;
    }

    @Override
    public int getPriority()
    {
        return priority;
    }

    @Override
    public void setPriority(int priority)
    {
        this.priority = priority;
    }

    @Override
    public boolean isLoaded(int position)
    {
        if (position < 0 || position >= size())
            throw new ArrayIndexOutOfBoundsException();

        return entries.get(position) != null;
    }

    @Override
    public void loadItems(int position, boolean exact)
    {
        loadItems(position, itemRetrievalRangeInOneDirection, exact);
    }

    private void loadItems(int position, int amountIntoEveryDirection, boolean exact)
    {
        if (position < 0 || position >= size())
            throw new ArrayIndexOutOfBoundsException();

        int newPosition = position;

        if (!exact)
        {
            //There may be items already in this range,
            //so we shift range out of known items to
            //maximize efficiency
            for (int i = -amountIntoEveryDirection; i <= 0; i++)
            {
                int absoluteLocation = i + position;

                if (absoluteLocation >= 0 && entries.get(absoluteLocation) == null)
                {
                    int amountToMove = amountIntoEveryDirection + i;

                    newPosition = position + amountToMove;
                    break;
                }
            }

            if (newPosition == position)
            {
                for (int i = amountIntoEveryDirection; i >= 0 ; i--)
                {
                    int absoluteLocation = i + position;

                    if (absoluteLocation < size && entries.get(absoluteLocation) == null)
                    {
                        int amountToMove = amountIntoEveryDirection - i;
                        newPosition = position - amountToMove;
                        break;
                    }
                }
            }
        }

        int from = Math.max(0, newPosition - amountIntoEveryDirection);
        int to = Math.min(size - 1, newPosition + amountIntoEveryDirection);

        connectionToProvider.requestItems(listPath, from, to);
    }

    @Override
    public void fillAround(int position, int amountIntoEveryDirection)
    {
        int from = Math.max(0, position - amountIntoEveryDirection);
        int to = Math.min(size - 1, position + amountIntoEveryDirection);

        boolean allLoaded = true;
        for (int i = from; i <= to; i++)
        {
            if (!isLoaded(i))
            {
                allLoaded = false;
                break;
            }
        }

        if (allLoaded)
            return;

        loadItems(position, amountIntoEveryDirection, false);
    }

    @Override
    public void invalidate()
    {
        this.size = 0;
        entries.evictAll();
        connectionToProvider.requestListSize(listPath);
    }

    @Override
    public void updateSizeReceived(String listPath, int newSize)
    {
        if (newSize == 0)
        {
            entries.evictAll();
        }
        else if (this.size() > newSize)
        {
            for (Integer key : entries.snapshot().keySet())
            {
                if (key < newSize)
                    entries.remove(key);
            }
        }

        this.size = newSize;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void dataReceived(String listPath, int from, Parcelable[] data)
    {
        for (int i = 0; i < data.length; i++)
        {
            entries.put(i + from, (E) data[i]);
        }
    }

    @Override
    public Parcelable.Creator getParcelableCreator(String listPath)
    {
        return creator;
    }

    @Override
    public void onError(String listPath, @TransferError int errorCode)
    {
    }
}
