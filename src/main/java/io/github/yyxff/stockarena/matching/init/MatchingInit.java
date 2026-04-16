package io.github.yyxff.stockarena.matching.init;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.MatchingEngineManager;
import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.OrderStatus;
import io.github.yyxff.stockarena.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MatchingInit {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MatchingEngineManager engineManager;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        System.out.println("Recovering pending orders from database to memory(matching engine)...");

        List<Order> pendingOrders = orderRepository.findByStatusIn(
                Arrays.asList(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED)
        );

        for (Order order : pendingOrders) {
            OrderMessage message = toOrderMessage(order);
            MatchingEngine engine = engineManager.getEngineBySymbol(message.getStockSymbol());
            engine.initOrder(message);
        }

        System.out.println("Recovered " + pendingOrders.size() + " orders to matching engines.");
        engineManager.markInitialized();
    }

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
