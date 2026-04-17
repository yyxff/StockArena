package io.github.yyxff.stockarena.matching.service.consumer;

import org.apache.rocketmq.client.consumer.AllocateMessageQueueStrategy;
import org.apache.rocketmq.common.message.MessageQueue;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A fixed queue-range allocation strategy for stateful consumers.
 *
 * Standard RocketMQ allocation strategies (e.g. average, consistent-hash) reassign
 * queues when consumer instances join or leave, causing symbol migrations that require
 * the matching engine to reload order books from DB.
 *
 * This strategy ignores the current set of live consumers entirely and always returns
 * the same preconfigured range of queues [queueStart, queueEnd].  Each instance is
 * deployed with its own range via environment variables:
 *
 *   MATCHING_CONSUMER_QUEUE_START=0   MATCHING_CONSUMER_QUEUE_END=15   → instance A
 *   MATCHING_CONSUMER_QUEUE_START=16  MATCHING_CONSUMER_QUEUE_END=31   → instance B
 *
 * Trade-off: if an instance goes down its queues are orphaned until manual intervention.
 * This is acceptable because migrating an in-memory order book is more expensive than
 * a brief accumulation of unprocessed messages.
 */
public class FixedRangeAllocateStrategy implements AllocateMessageQueueStrategy {

    private final int queueStart;
    private final int queueEnd;

    public FixedRangeAllocateStrategy(int queueStart, int queueEnd) {
        this.queueStart = queueStart;
        this.queueEnd = queueEnd;
    }

    @Override
    public List<MessageQueue> allocate(String consumerGroup, String currentCID,
                                       List<MessageQueue> mqAll, List<String> cidAll) {
        return mqAll.stream()
                .filter(mq -> mq.getQueueId() >= queueStart && mq.getQueueId() <= queueEnd)
                .collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return "FIXED_RANGE";
    }
}
