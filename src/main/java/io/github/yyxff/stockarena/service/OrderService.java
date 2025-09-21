package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.config.KafkaTopics;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.OrderRequest;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.OrderStatus;
import io.github.yyxff.stockarena.repository.OrderRepository;
import jakarta.transaction.Transactional;
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

    private static final String ORDER_TOPIC = KafkaTopics.ORDERS;

    @Transactional
    public void placeBuyOrder(OrderRequest orderRequest) {
        // 1. Validate order
        validateBuyOrder(orderRequest);

        // 2. Freeze balance
        BigDecimal totalPrice = orderRequest.getPrice().multiply(BigDecimal.valueOf(orderRequest.getQuantity()));
        accountService.freezeBalance(orderRequest.getAccountId(), totalPrice);

        // 3. Save new order
        Order order = saveNewOrder(orderRequest);

        // 4. Send order to MQ
        sendOrderToMQ(order);

        return; // Success
    }

    @Transactional
    public void placeSellOrder(OrderRequest orderRequest) {
        // 1. Validate order
        validateSellOrder(orderRequest);

        // 2. Freeze shares
        portfolioService.freezeShares(orderRequest.getAccountId(), orderRequest.getStockSymbol(), orderRequest.getQuantity());

        // 3. Save new order
        Order order = saveNewOrder(orderRequest);

        // 4. Send order to MQ
        sendOrderToMQ(order);
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

    private Order saveNewOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setAccountId(orderRequest.getAccountId());
        order.setStockSymbol(orderRequest.getStockSymbol());
        order.setPrice(orderRequest.getPrice());
        order.setTotalQuantity(orderRequest.getQuantity());
        order.setRemainingQuantity(orderRequest.getQuantity());
        order.setOrderType(orderRequest.getOrderType());
        order.setStatus(OrderStatus.OPEN);
        return orderRepository.save(order);
    }

    private void sendOrderToMQ(Order order) {
        // 1. Construct OrderMessage
        OrderMessage msg = new OrderMessage();
        msg.setOrderId(order.getId());
        msg.setAccountId(order.getAccountId());
        msg.setStockSymbol(order.getStockSymbol());
        msg.setOrderType(order.getOrderType());
        msg.setPrice(order.getPrice());
        msg.setTotalQuantity(order.getTotalQuantity());
        msg.setRemainingQuantity(order.getRemainingQuantity());
        msg.setCreatedAt(order.getCreatedAt());

        // 2. Send to MQ
        kafkaTemplate.send(ORDER_TOPIC, order.getStockSymbol(), msg);
    }

    @Transactional
    public void matchOrder(Order buyOrder, Order sellOrder) {
        int sellQuantity = sellOrder.getRemainingQuantity();
        int buyQuantity = buyOrder.getRemainingQuantity();
        int matchedQuantity = Math.min(sellQuantity, buyQuantity);

        // Update order statuses and remaining quantities
        sellOrder.setRemainingQuantity(sellQuantity - matchedQuantity);
        buyOrder.setRemainingQuantity(buyQuantity - matchedQuantity);
        if (sellOrder.getRemainingQuantity() == 0) {
            sellOrder.setStatus(OrderStatus.FILLED);
        } else {
            sellOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
        if (buyOrder.getRemainingQuantity() == 0) {
            buyOrder.setStatus(OrderStatus.FILLED);
        } else {
            buyOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
        orderRepository.save(sellOrder);
        orderRepository.save(buyOrder);
    }
}
