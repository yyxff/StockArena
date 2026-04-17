package io.github.yyxff.stockarena.matching.service.consumer;

import io.github.yyxff.stockarena.dto.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Receives committed order messages from order-topic (transactional, random queue)
 * and re-publishes them to order-routing-topic using syncSendOrderly with the stock
 * symbol as the hash key.
 *
 * This two-hop design decouples two separate concerns:
 *  1. order-topic (Transaction type)  — atomicity between DB freeze and MQ publish
 *  2. order-routing-topic (FIFO type) — per-symbol ordering and fixed consumer routing
 *
 * RocketMQ requires Transaction and FIFO to be separate topic types, so the two hops
 * are unavoidable.  syncSendOrderly routes same symbol to the same queue deterministically
 * via hash(symbol) % queueCount — equivalent to setting MessageGroup = symbol in 5.x.
 *
 * Idempotency: if this consumer retries (re-publishes the same orderId twice),
 * the OrderDeduplicator in the matching engine drops the duplicate.
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order-router"
)
public class MatchingConsumer implements RocketMQListener<OrderMessage> {

    static final String ORDER_ROUTING_TOPIC = "order-routing-topic";

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(OrderMessage orderMessage) {
        // Route to the per-symbol ordered topic.  Same symbol always lands on the
        // same queue (and therefore the same consumer instance).
        rocketMQTemplate.syncSendOrderly(
                ORDER_ROUTING_TOPIC, orderMessage, orderMessage.getStockSymbol());
        log.debug("Routed order {} symbol={} to {}",
                orderMessage.getOrderId(), orderMessage.getStockSymbol(), ORDER_ROUTING_TOPIC);
    }
}
