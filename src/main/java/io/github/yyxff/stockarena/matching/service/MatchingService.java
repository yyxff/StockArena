package io.github.yyxff.stockarena.matching.service;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.model.Account;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.Portfolio;
import io.github.yyxff.stockarena.repository.AccountRepository;
import io.github.yyxff.stockarena.repository.OrderRepository;
import io.github.yyxff.stockarena.repository.PortfolioRepository;
import io.github.yyxff.stockarena.service.AccountService;
import io.github.yyxff.stockarena.service.PortfolioService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class MatchingService {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private PortfolioService portfolioService;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private AccountRepository accountRepository;

    @Transactional
    public void match(OrderMessage orderMessage) {
        switch (orderMessage.getOrderType()) {
            case BUY:
                System.out.println("Matching buy order: " + orderMessage);
                Order order = orderRepository.findById(orderMessage.getOrderId()).orElseThrow( () -> new IllegalArgumentException("Order not found: " + orderMessage.getOrderId()));
                while (order.getRemainingQuantity() > 0) {
                    matchBuyOrder(order);
                }
                break;
            case SELL:
                System.out.println("Matching sell order: " + orderMessage);
                break;
            default:
                throw new IllegalArgumentException("Unknown order type: " + orderMessage.getOrderType());
        }
    }

    private void matchBuyOrder(Order buyOrder) {
        // 1. Search for the best matching sell order
        Optional<Order> sellOpt = orderRepository.findBestSellOrder(
                buyOrder.getStockSymbol(), buyOrder.getPrice()
        );

        // 2. If not found, return
        if (sellOpt.isEmpty()) {
            return;
        }

        // 3. If found, match the orders
        Order sellOrder = sellOpt.get();
        int sellQuantity = sellOrder.getRemainingQuantity();
        int buyQuantity = buyOrder.getRemainingQuantity();
        int matchedQuantity = Math.min(sellQuantity, buyQuantity);

        // Update order statuses and remaining quantities
        sellOrder.setRemainingQuantity(sellQuantity - matchedQuantity);
        buyOrder.setRemainingQuantity(buyQuantity - matchedQuantity);
        orderRepository.save(sellOrder);
        orderRepository.save(buyOrder);

        // Add buyer balance
        BigDecimal totalPrice = sellOrder.getPrice().multiply(java.math.BigDecimal.valueOf(matchedQuantity));
        accountService.addBalance(buyOrder.getAccountId(), totalPrice);

        // Add buyer portfolio
        Portfolio buyerPortfolio = portfolioRepository.findByAccountIdAndStockSymbol(buyOrder.getAccountId(), buyOrder.getStockSymbol())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));
        portfolioService.deductShares(buyOrder.getAccountId(), buyOrder.getStockSymbol(), buyQuantity);

        // Deduct seller balance
        accountService.deductBalance(buyOrder.getAccountId(), totalPrice);

        // Deduct seller portfolio
        Portfolio sellerPortfolio = portfolioRepository.findByAccountIdAndStockSymbol(sellOrder.getAccountId(), sellOrder.getStockSymbol())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));
        portfolioService.deductShares(sellOrder.getAccountId(), sellOrder.getStockSymbol(), sellQuantity);
    }
}
