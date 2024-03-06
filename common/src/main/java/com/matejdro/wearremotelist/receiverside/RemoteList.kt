package com.matejdro.wearremotelist.receiverside

import android.os.Parcelable

interface RemoteList<E : Parcelable?> {
    /**
     * @return Identifier path for this list.
     */
    val path: String

    /**
     * Returns item at specified position in the list.
     * If item is not yet loaded, this method will return `null` and begin retrieving this data from the provider.
     * @param position position of the item.
     * @return Item at this position or `null`.
     * @throws ArrayIndexOutOfBoundsException if specified position is out of list bounds.
     */
    operator fun get(position: Int): E
    /**
     * For performance reasons, only one list can transfer items at a time.
     * If two lists wants to transfer at the same time, one with higher priority will transfer first.
     * Defalut priority of every list is `0`.
     * @return Priority of a list.
     */
    /**
     * Sets priority of this list.
     * @see .getPriority
     */
    var priority: Int

    /**
     * @return Amount of items in this list.
     */
    fun size(): Int

    /**
     * Check if this item is loaded.
     * This method will not automatically start retrieving data from the provider if item is missing.
     * @param position position of the item.
     * @return `true` if this item is loaded, `false` otherwise.
     * @throws ArrayIndexOutOfBoundsException if specified position is out of list bounds.
     */
    fun isLoaded(position: Int): Boolean

    /**
     * Loads specific items and items around it from the provider.
     * @param position Position of the item that will be loaded (and items around it).
     * @param exact When `false`, position may be moved to fill as many missing items in the list as possible instead of reloading existing items again. Set to `true` if you don't care about missing items and you want to reload specific items from receiver.
     * @throws ArrayIndexOutOfBoundsException if specified position is out of list bounds.
     */
    suspend fun loadItems(position: Int, exact: Boolean)

    /**
     * Load specific amount of items around specific position.
     * This method is similar to [.loadItems], but it will not do anything if all items in specified range are already loaded.
     * @param position Center position.
     * @param amountIntoEveryDirection Amount to load in both directions (up and down).
     */
    suspend fun fillAround(position: Int, amountIntoEveryDirection: Int)

    /**
     * Clears all cached items on the receiver, resets list size to 0 and requests actual item size from the provider.
     */
    suspend fun invalidate()
}
