package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.dto.OrderRequest;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.repository.OrderRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderService {

    @Autowired
    private AccountService accountService;
    @Autowired
    private OrderRepository orderRepository;

    @Transactional
    public void placeBuyOrder(OrderRequest orderRequest) {
        // 1. Validate order
        validateBuyOrder(orderRequest);

        // 2. Deduct balance
        BigDecimal totalPrice = orderRequest.getPrice().multiply(BigDecimal.valueOf(orderRequest.getQuantity()));
        accountService.deductBalance(orderRequest.getAccountId(), totalPrice);

        // 3. Save new order
        SaveNewOrder(orderRequest);

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

    private void SaveNewOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setAccountId(orderRequest.getAccountId());
        order.setPrice(orderRequest.getPrice());
        order.setTotalQuantity(orderRequest.getQuantity());
        order.setRemainingQuantity(orderRequest.getQuantity());
        order.setOrderType(orderRequest.getOrderType());
        orderRepository.save(order);
    }
}
