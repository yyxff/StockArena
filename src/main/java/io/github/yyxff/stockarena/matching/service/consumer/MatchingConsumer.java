package io.github.yyxff.stockarena.matching.service.consumer;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.MatchingEngineManager;
import io.github.yyxff.stockarena.matching.service.MatchingEngine;
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
 * Returning normally from onMessage is sufficient to acknowledge the message;
 * order processing cannot fail at this stage.
 */
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
        MatchingEngine engine = matchingEngineManager.getEngineBySymbol(orderMessage.getStockSymbol());
        engine.dispatch(orderMessage);
    }
}
