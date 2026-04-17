package io.github.yyxff.stockarena.matching.service.consumer;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.MatchingEngineManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQConsumerLifecycleListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Orderly consumer for per-symbol matching engine dispatch.
 *
 * Consumes from order-routing-topic where same symbol always lands on the same
 * queue (via syncSendOrderly in MatchingConsumer).  ORDERLY consume mode ensures
 * each queue is processed by a single thread, so per-symbol message ordering is
 * fully preserved end-to-end.
 *
 * Queue assignment is fixed via FixedRangeAllocateStrategy: each deployed instance
 * handles a preconfigured queue range, preventing symbol migrations during rebalance.
 * Configure per instance:
 *   MATCHING_CONSUMER_QUEUE_START=0   MATCHING_CONSUMER_QUEUE_END=15
 *
 * The isInitialized() gate ensures no orders are dispatched to the matching engine
 * until MatchingInit has finished loading historical open orders from DB.  With
 * ORDERLY mode, throwing an exception blocks the queue and causes RocketMQ to retry,
 * which is the desired back-pressure behaviour during startup.
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "order-routing-topic",
        consumerGroup = "order-matcher",
        consumeMode = ConsumeMode.ORDERLY
)
public class MatchingEngineConsumer implements RocketMQListener<OrderMessage>,
        RocketMQConsumerLifecycleListener<DefaultMQPushConsumer> {

    @Autowired
    private MatchingEngineManager matchingEngineManager;

    @Value("${matching.consumer.queue-start:0}")
    private int queueStart;

    @Value("${matching.consumer.queue-end:63}")
    private int queueEnd;

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setAllocateMessageQueueStrategy(
                new FixedRangeAllocateStrategy(queueStart, queueEnd));
        log.info("MatchingEngineConsumer allocated queues [{}, {}]", queueStart, queueEnd);
    }

    @Override
    public void onMessage(OrderMessage orderMessage) {
        if (!matchingEngineManager.isInitialized()) {
            log.warn("Engine not yet initialized, rejecting order {} for retry",
                    orderMessage.getOrderId());
            throw new IllegalStateException("Matching engine not yet initialized");
        }
        matchingEngineManager.getEngineBySymbol(orderMessage.getStockSymbol())
                .dispatch(orderMessage);
    }
}
