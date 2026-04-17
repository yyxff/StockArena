package io.github.yyxff.stockarena.matching.service.consumer;

import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FixedRangeAllocateStrategyTest {

    /** Build a list of MessageQueues with queueId 0..count-1 */
    private List<MessageQueue> queues(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new MessageQueue("order-routing-topic", "broker-a", i))
                .toList();
    }

    @Test
    void returnsOnlyQueuesInConfiguredRange() {
        var strategy = new FixedRangeAllocateStrategy(16, 31);
        List<MessageQueue> allQueues = queues(64);

        List<MessageQueue> assigned = strategy.allocate("order-matcher", "instance-B", allQueues, List.of());

        assertThat(assigned).hasSize(16);
        assertThat(assigned).allMatch(mq -> mq.getQueueId() >= 16 && mq.getQueueId() <= 31);
    }

    @Test
    void fullRangeReturnedForSingleInstance() {
        var strategy = new FixedRangeAllocateStrategy(0, 63);
        List<MessageQueue> allQueues = queues(64);

        List<MessageQueue> assigned = strategy.allocate("order-matcher", "instance-A", allQueues, List.of());

        assertThat(assigned).hasSize(64);
    }

    @Test
    void ignoresOtherConsumersInGroup() {
        // Even if three other consumers are alive, assignment must not change
        var strategy = new FixedRangeAllocateStrategy(0, 15);
        List<MessageQueue> allQueues = queues(64);
        List<String> fourConsumers = List.of("instance-A", "instance-B", "instance-C", "instance-D");

        List<MessageQueue> assigned = strategy.allocate("order-matcher", "instance-A", allQueues, fourConsumers);

        assertThat(assigned).hasSize(16);
        assertThat(assigned).allMatch(mq -> mq.getQueueId() >= 0 && mq.getQueueId() <= 15);
    }

    @Test
    void returnsEmptyWhenNoQueuesInRange() {
        // Range configured beyond the actual queue count
        var strategy = new FixedRangeAllocateStrategy(100, 120);
        List<MessageQueue> allQueues = queues(64);

        List<MessageQueue> assigned = strategy.allocate("order-matcher", "instance-X", allQueues, List.of());

        assertThat(assigned).isEmpty();
    }

    @Test
    void strategyNameIsFixedRange() {
        assertThat(new FixedRangeAllocateStrategy(0, 63).getName()).isEqualTo("FIXED_RANGE");
    }
}
