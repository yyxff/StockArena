package io.github.yyxff.stockarena.matching.service.consumer;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.model.OrderType;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingConsumerTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private MatchingConsumer matchingConsumer;

    private OrderMessage order;

    @BeforeEach
    void setUp() {
        order = new OrderMessage();
        order.setOrderId(123L);
        order.setStockSymbol("AAPL");
        order.setOrderType(OrderType.BUY);
    }

    @Test
    void forwardsToOrderRoutingTopicWithSymbolAsHashKey() {
        matchingConsumer.onMessage(order);

        verify(rocketMQTemplate).syncSendOrderly(
                MatchingConsumer.ORDER_ROUTING_TOPIC,
                order,
                "AAPL"
        );
    }

    @Test
    void usesDifferentHashKeyPerSymbol() {
        OrderMessage googOrder = new OrderMessage();
        googOrder.setOrderId(456L);
        googOrder.setStockSymbol("GOOG");

        matchingConsumer.onMessage(order);
        matchingConsumer.onMessage(googOrder);

        verify(rocketMQTemplate).syncSendOrderly(MatchingConsumer.ORDER_ROUTING_TOPIC, order, "AAPL");
        verify(rocketMQTemplate).syncSendOrderly(MatchingConsumer.ORDER_ROUTING_TOPIC, googOrder, "GOOG");
    }
}
