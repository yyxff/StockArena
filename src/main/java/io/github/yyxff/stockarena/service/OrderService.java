package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.common.IdGenerator;
import io.github.yyxff.stockarena.config.KafkaTopics;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.OrderRequest;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.OrderStatus;
import io.github.yyxff.stockarena.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderService {

    @Autowired
    private AccountService accountService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PortfolioService portfolioService;
    @Autowired
    private KafkaTemplate kafkaTemplate;
    @Autowired
    private IdGenerator idGenerator;

    private static final String ORDER_TOPIC = KafkaTopics.ORDERS;

    @Transactional
    public void placeBuyOrder(OrderRequest orderRequest) {
        // 1. Validate order
        validateBuyOrder(orderRequest);

        // 2. Freeze balance
        BigDecimal totalPrice = orderRequest.getPrice().multiply(BigDecimal.valueOf(orderRequest.getQuantity()));
        accountService.freezeBalance(orderRequest.getAccountId(), totalPrice);

        // 3. Form new order msg
        OrderMessage orderMsg = formNewOrderMsg(orderRequest);

        // 4. Send order to MQ
        sendOrderToMQ(orderMsg);

        return; // Success
    }

    @Transactional
    public void placeSellOrder(OrderRequest orderRequest) {
        // 1. Validate order
        validateSellOrder(orderRequest);

        // 2. Freeze shares
        portfolioService.freezeShares(orderRequest.getAccountId(), orderRequest.getStockSymbol(), orderRequest.getQuantity());

        // 3. Form new order msg
        OrderMessage orderMsg = formNewOrderMsg(orderRequest);

        // 4. Send order to MQ
        sendOrderToMQ(orderMsg);

        return; // Success
    }

    private void validateBuyOrder(OrderRequest orderRequest) {
        // Check quantity
        if (orderRequest.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        // Check price
        if (orderRequest.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        // Check account balance
        BigDecimal totalPrice = orderRequest.getPrice().multiply(BigDecimal.valueOf(orderRequest.getQuantity()));
        if (accountService.getAvailableBalance(orderRequest.getAccountId()).compareTo(totalPrice) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }
    }

    private void validateSellOrder(OrderRequest orderRequest) {
        // Check quantity
        if (orderRequest.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        // Check price
        if (orderRequest.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        // Check holdings
        if (portfolioService.getAvailableShares(orderRequest.getAccountId(), orderRequest.getStockSymbol()) < orderRequest.getQuantity()) {
            throw new IllegalArgumentException("Insufficient shares to sell");
        }
    }

    private OrderMessage formNewOrderMsg(OrderRequest orderRequest) {
        OrderMessage orderMsg = new OrderMessage();
        orderMsg.setOrderId(idGenerator.nextId());
        orderMsg.setAccountId(orderRequest.getAccountId());
        orderMsg.setStockSymbol(orderRequest.getStockSymbol());
        orderMsg.setPrice(orderRequest.getPrice());
        orderMsg.setTotalQuantity(orderRequest.getQuantity());
        orderMsg.setRemainingQuantity(orderRequest.getQuantity());
        orderMsg.setOrderType(orderRequest.getOrderType());
        orderMsg.setOrderStatus(OrderStatus.OPEN);
        orderMsg.setCreatedAt(java.time.LocalDateTime.now());
        return orderMsg;
    }

    private void sendOrderToMQ(OrderMessage orderMsg) {
        kafkaTemplate.send(ORDER_TOPIC, orderMsg.getStockSymbol(), orderMsg);
    }
}
