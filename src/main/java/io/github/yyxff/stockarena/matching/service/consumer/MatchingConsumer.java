package io.github.yyxff.stockarena.matching.service.consumer;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.MatchingEngineManager;
import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Consumes order messages from RocketMQ and dispatches them to the correct
 * MatchingEngine by stock symbol hash.
 *
 * Same symbol always routes to the same engine (and worker), preserving
 * per-symbol ordering without requiring FIFO queues in the broker.
 *
 * Returning normally from onMessage acknowledges the message; throwing causes
 * RocketMQ to delay and retry, which is used as a back-pressure gate while
 * MatchingInit is still loading historical orders into the order books.
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order-matcher"
)
public class MatchingConsumer implements RocketMQListener<OrderMessage> {

    @Autowired
    private MatchingEngineManager matchingEngineManager;

    @Override
    public void onMessage(OrderMessage orderMessage) {
        if (!matchingEngineManager.isInitialized()) {
            // Order books are still being loaded from DB; reject so RocketMQ retries
            // after a delay rather than dispatching into an incomplete order book.
            log.warn("Matching engine not yet initialized, rejecting order {} for retry",
                    orderMessage.getOrderId());
            throw new IllegalStateException("Matching engine not yet initialized");
        }
        MatchingEngine engine = matchingEngineManager.getEngineBySymbol(orderMessage.getStockSymbol());
        engine.dispatch(orderMessage);
    }
}
