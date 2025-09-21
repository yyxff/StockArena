package io.github.yyxff.stockarena.matching.init;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.MatchingEngineManager;
import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.OrderStatus;
import io.github.yyxff.stockarena.model.OrderType;
import io.github.yyxff.stockarena.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class MatchingInit {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MatchingEngineManager engineManager;

    private final int partitionCount = 4; // Kafka topic partitions count

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        System.out.println("Recovering pending orders from database to memory(matching engine)...");

        // 1. Select pending orders from DB
        List<Order> pendingOrders = orderRepository.findByStatusIn(
                Arrays.asList(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED)
        );

        for (Order order : pendingOrders) {
            OrderMessage message = toOrderMessage(order);
            System.out.println("recovering " + message);

            // Assign to partition
            // Same hash logic as Kafka producer
            int partition = getKafkaPartition(message.getStockSymbol(), partitionCount);

            // Get corresponding MatchingEngine
            MatchingEngine engine = engineManager.getEngineByPartition(partition);

            // Update in-memory order book
            System.out.println(partition + ": " + message);
            engine.initOrder(message);
        }

        System.out.println("Recovered totally " + pendingOrders.size() + " orders to matching engines.");
    }

    private int getKafkaPartition(String key, int partitionCount) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        return org.apache.kafka.common.utils.Utils.toPositive(
                org.apache.kafka.common.utils.Utils.murmur2(keyBytes)
        ) % partitionCount;
    }

    // Order -> OrderMessage
    private OrderMessage toOrderMessage(Order order) {
        OrderMessage msg = new OrderMessage();
        msg.setOrderId(order.getId());
        msg.setAccountId(order.getAccountId());
        msg.setStockSymbol(order.getStockSymbol());
        msg.setOrderType(order.getOrderType());
        msg.setPrice(order.getPrice());
        msg.setTotalQuantity(order.getTotalQuantity());
        msg.setRemainingQuantity(order.getRemainingQuantity());
        msg.setCreatedAt(order.getCreatedAt());
        return msg;
    }
}
