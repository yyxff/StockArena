package io.github.yyxff.stockarena.mq.order;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.OrderType;
import io.github.yyxff.stockarena.repository.OrderRepository;
import io.github.yyxff.stockarena.service.AccountService;
import io.github.yyxff.stockarena.service.PortfolioService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderLocalTransactionService {

    @Autowired
    private AccountService accountService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Freeze funds and persist the order atomically.
     * Called inside the RocketMQ local transaction callback.
     */
    @Transactional
    public void freezeAndSaveOrder(OrderMessage orderMsg) {
        if (orderMsg.getOrderType() == OrderType.BUY) {
            BigDecimal total = orderMsg.getPrice()
                    .multiply(BigDecimal.valueOf(orderMsg.getTotalQuantity()));
            accountService.freezeBalance(orderMsg.getAccountId(), total);
        } else {
            portfolioService.freezeShares(
                    orderMsg.getAccountId(),
                    orderMsg.getStockSymbol(),
                    orderMsg.getTotalQuantity());
        }

        Order order = new Order();
        order.setId(orderMsg.getOrderId());
        order.setAccountId(orderMsg.getAccountId());
        order.setStockSymbol(orderMsg.getStockSymbol());
        order.setTotalQuantity(orderMsg.getTotalQuantity());
        order.setRemainingQuantity(orderMsg.getRemainingQuantity());
        order.setPrice(orderMsg.getPrice());
        order.setOrderType(orderMsg.getOrderType());
        order.setStatus(orderMsg.getOrderStatus());
        order.setCreatedAt(orderMsg.getCreatedAt());
        orderRepository.save(order);
    }
}
