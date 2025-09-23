package io.github.yyxff.stockarena.matching;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import io.github.yyxff.stockarena.dto.OrderMessage;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OrderDeduplicator {

    // Active (not matched yet)
    private final Set<Long> activeOrders =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Recently matched oerders (LRU)
    private final Cache<Long, Boolean> completedOrders;

    // Bloom filter
    private final BloomFilter<Long> bloomFilter;

    public OrderDeduplicator(int lruCapacity, int bloomExpectedInsertions, double fpp) {
        this.completedOrders = Caffeine.newBuilder()
                .maximumSize(lruCapacity)
                .expireAfterWrite(1, TimeUnit.HOURS) // 1h expiration
                .build();

        this.bloomFilter = BloomFilter.create(
                Funnels.longFunnel(),
                bloomExpectedInsertions,
                fpp
        );
    }

    /**
     * Judge if order has been processed
     */
    public boolean isDuplicate(long orderId) {
        // In active orders -> true
        if (activeOrders.contains(orderId)) {
            return true;
        }

        // If Bloom thinks it is not here, return false
        if (!bloomFilter.mightContain(orderId)) {
            return false;
        }

        // Bloom thinks it was here before -> check Completed LRU
        return completedOrders.getIfPresent(orderId) != null;
    }

    /**
     * Mark this order as active
     */
    public void markActive(long orderId) {
        activeOrders.add(orderId);
    }

    /**
     * Mark order as complete (remove from active set, put it into completed + bloom)
     */
    public void markCompleted(long orderId) {
        activeOrders.remove(orderId);
        completedOrders.put(orderId, true);
        bloomFilter.put(orderId);
    }

    /**
     * Mark order as complete (remove from active set, put it into completed + bloom)
     */
    public void markCompleted(List<OrderMessage> filledOrders) {
        for (OrderMessage order : filledOrders) {
            long orderId = order.getOrderId();
            activeOrders.remove(orderId);
            completedOrders.put(orderId, true);
            bloomFilter.put(orderId);
        }
    }
}