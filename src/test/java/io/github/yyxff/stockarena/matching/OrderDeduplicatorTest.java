package io.github.yyxff.stockarena.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDeduplicatorTest {

    private OrderDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new OrderDeduplicator();
    }

    @Test
    void newOrderIsNotDuplicate() {
        assertThat(deduplicator.isDuplicate(1001L)).isFalse();
    }

    @Test
    void orderIsDuplicateAfterMarkSeen() {
        deduplicator.markSeen(1001L);
        assertThat(deduplicator.isDuplicate(1001L)).isTrue();
    }

    @Test
    void unseenOrderRemainsUnseenAfterOtherMarkSeen() {
        deduplicator.markSeen(1001L);
        assertThat(deduplicator.isDuplicate(1002L)).isFalse();
    }

    @Test
    void multipleOrdersTrackedIndependently() {
        deduplicator.markSeen(1001L);
        deduplicator.markSeen(1002L);
        deduplicator.markSeen(1003L);

        assertThat(deduplicator.isDuplicate(1001L)).isTrue();
        assertThat(deduplicator.isDuplicate(1002L)).isTrue();
        assertThat(deduplicator.isDuplicate(1003L)).isTrue();
        assertThat(deduplicator.isDuplicate(1004L)).isFalse();
    }

    @Test
    void handlesSnowflakeIdRange() {
        // Snowflake IDs: ~41-bit timestamp prefix, values near Long.MAX_VALUE / 2
        long snowflake1 = 7241234567890123001L;
        long snowflake2 = 7241234567890123002L;

        deduplicator.markSeen(snowflake1);

        assertThat(deduplicator.isDuplicate(snowflake1)).isTrue();
        assertThat(deduplicator.isDuplicate(snowflake2)).isFalse();
    }

    @Test
    void handlesLargeVolumeOfOrders() {
        int count = 100_000;
        for (long i = 0; i < count; i++) {
            deduplicator.markSeen(i);
        }
        for (long i = 0; i < count; i++) {
            assertThat(deduplicator.isDuplicate(i)).isTrue();
        }
        assertThat(deduplicator.isDuplicate(count)).isFalse();
    }

    @Test
    void concurrentMarkSeenAndIsDuplicateAreSafe() throws InterruptedException {
        int threads = 8;
        int ordersPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final long base = (long) t * ordersPerThread;
            executor.submit(() -> {
                try {
                    for (long i = base; i < base + ordersPerThread; i++) {
                        deduplicator.markSeen(i);
                        // markSeen then isDuplicate must be consistent
                        if (!deduplicator.isDuplicate(i)) {
                            errors.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertThat(errors.get()).isZero();
    }
}
