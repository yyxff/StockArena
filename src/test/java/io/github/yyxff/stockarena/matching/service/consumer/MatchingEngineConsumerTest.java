package io.github.yyxff.stockarena.matching.service.consumer;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.MatchingEngineManager;
import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import io.github.yyxff.stockarena.model.OrderType;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingEngineConsumerTest {

    @Mock
    private MatchingEngineManager matchingEngineManager;

    @Mock
    private MatchingEngine matchingEngine;

    @InjectMocks
    private MatchingEngineConsumer consumer;

    private OrderMessage order;

    @BeforeEach
    void setUp() {
        // Inject @Value fields (not set by Mockito)
        ReflectionTestUtils.setField(consumer, "queueStart", 0);
        ReflectionTestUtils.setField(consumer, "queueEnd", 63);

        order = new OrderMessage();
        order.setOrderId(1L);
        order.setStockSymbol("AAPL");
        order.setOrderType(OrderType.BUY);
    }

    @Test
    void throwsWhenEngineNotYetInitialized() {
        when(matchingEngineManager.isInitialized()).thenReturn(false);

        assertThatThrownBy(() -> consumer.onMessage(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not yet initialized");

        verify(matchingEngineManager, never()).getEngineBySymbol(any());
    }

    @Test
    void dispatchesToCorrectEngineWhenInitialized() {
        when(matchingEngineManager.isInitialized()).thenReturn(true);
        when(matchingEngineManager.getEngineBySymbol("AAPL")).thenReturn(matchingEngine);

        consumer.onMessage(order);

        verify(matchingEngineManager).getEngineBySymbol("AAPL");
        verify(matchingEngine).dispatch(order);
    }

    @Test
    void prepareStartRegistersFixedRangeStrategy() {
        DefaultMQPushConsumer pushConsumer = mock(DefaultMQPushConsumer.class);

        consumer.prepareStart(pushConsumer);

        verify(pushConsumer).setAllocateMessageQueueStrategy(
                argThat(s -> s.getName().equals("FIXED_RANGE")));
    }
}
