package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.config.KafkaTopics;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.OrderStatus;
import io.github.yyxff.stockarena.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class OrderPersistenceService {

    @Autowired
    private OrderRepository orderRepository;

    @KafkaListener(topics = KafkaTopics.ORDERS,
                   groupId = "order-persistence-group",
                   concurrency = "4")
    public void handleOrderPersistence(@Payload OrderMessage orderMessage,
                                       @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                       @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            log.info("Processing order persistence for orderId: {}, offset: {}",
                    orderMessage.getOrderId(), offset);

            // If order already exists, skip
            if (orderRepository.existsById(orderMessage.getOrderId())) {
                log.info("Order {} already exists, skipping persistence",
                        orderMessage.getOrderId());
                return;
            }

            // Convert and save order
            Order order = convertToOrder(orderMessage);
            orderRepository.save(order);

            log.info("Order {} persisted successfully", orderMessage.getOrderId());

        } catch (DataIntegrityViolationException e) {
            // If unique constraint violation, it means order already exists
            log.warn("Order {} already exists due to constraint violation",
                    orderMessage.getOrderId());
        } catch (Exception e) {
            log.error("Failed to persist order {}", orderMessage.getOrderId(), e);
            // Retry by not ack mq, let mq resend
            throw e;
        }
    }

    private Order convertToOrder(OrderMessage orderMessage) {
        Order order = new Order();
        order.setId(orderMessage.getOrderId());
        order.setAccountId(orderMessage.getAccountId());
        order.setStockSymbol(orderMessage.getStockSymbol());
        order.setPrice(orderMessage.getPrice());
        order.setTotalQuantity(orderMessage.getTotalQuantity());
        order.setRemainingQuantity(orderMessage.getRemainingQuantity());
        order.setOrderType(orderMessage.getOrderType());
        order.setStatus(OrderStatus.OPEN);
        order.setCreatedAt(orderMessage.getCreatedAt());
        return order;
    }
}
