package io.github.yyxff.stockarena.matching;

import org.roaringbitmap.longlong.Roaring64NavigableMap;

/**
 * Exact deduplication of order IDs using a 64-bit Roaring Bitmap.
 *
 * Snowflake IDs are 64-bit longs with high temporal locality (same-millisecond
 * IDs share the top 41 bits), which gives Roaring Bitmap excellent compression.
 *
 * Not thread-safe by itself; callers must synchronize if accessed from
 * multiple threads.
 */
public class OrderDeduplicator {

    private final Roaring64NavigableMap seenOrders = new Roaring64NavigableMap();

    /**
     * Returns true if this order ID has already been seen (either queued or
     * recovered from DB at startup).
     */
    public synchronized boolean isDuplicate(long orderId) {
        return seenOrders.contains(orderId);
    }

    /**
     * Mark an order ID as seen so that future duplicates are rejected.
     */
    public synchronized void markSeen(long orderId) {
        seenOrders.addLong(orderId);
    }
}
