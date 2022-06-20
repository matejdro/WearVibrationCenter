package com.matejdro.wearremotelist.receiverside;

import android.os.Parcelable;

public interface RemoteList<E extends Parcelable>
{
    /**
     * @return Identifier path for this list.
     */
    String getPath();

    /**
     * Returns item at specified position in the list.
     * If item is not yet loaded, this method will return {@code null} and begin retrieving this data from the provider.
     * @param position position of the item.
     * @return Item at this position or {@code null}.
     * @throws ArrayIndexOutOfBoundsException if specified position is out of list bounds.
     */
    E get(int position);

    /**
     * For performance reasons, only one list can transfer items at a time.
     * If two lists wants to transfer at the same time, one with higher priority will transfer first.
     * Defalut priority of every list is {@code 0}.
     * @return Priority of a list.
     */
    int getPriority();

    /**
     * Sets priority of this list.
     * @see #getPriority()
     */
    void setPriority(int priority);

    /**
     * @return Amount of items in this list.
     */
    int size();

    /**
     * Check if this item is loaded.
     * This method will not automatically start retrieving data from the provider if item is missing.
     * @param position position of the item.
     * @return {@code true} if this item is loaded, {@code false} otherwise.
     * @throws ArrayIndexOutOfBoundsException if specified position is out of list bounds.
     */
    boolean isLoaded(int position);

    /**
     * Loads specific items and items around it from the provider.
     * @param position Position of the item that will be loaded (and items around it).
     * @param exact When {@code false}, position may be moved to fill as many missing items in the list as possible instead of reloading existing items again. Set to {@code true} if you don't care about missing items and you want to reload specific items from receiver.
     * @throws ArrayIndexOutOfBoundsException if specified position is out of list bounds.
     */
    void loadItems(int position, boolean exact);

    /**
     * Load specific amount of items around specific position.
     * This method is similar to {@link #loadItems(int, boolean)}, but it will not do anything if all items in specified range are already loaded.
     * @param position Center position.
     * @param amountIntoEveryDirection Amount to load in both directions (up and down).
     */
    void fillAround(int position, int amountIntoEveryDirection);

    /**
     * Clears all cached items on the receiver, resets list size to 0 and requests actual item size from the provider.
     */
    void invalidate();
}
