package io.github.yyxff.stockarena.matching.service;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.OrderBook;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.repository.AccountRepository;
import io.github.yyxff.stockarena.repository.OrderRepository;
import io.github.yyxff.stockarena.repository.PortfolioRepository;
import io.github.yyxff.stockarena.service.AccountService;
import io.github.yyxff.stockarena.service.OrderService;
import io.github.yyxff.stockarena.service.PortfolioService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MatchingEngine {

    // @Autowired
    // private OrderRepository orderRepository;
    // @Autowired
    // private AccountService accountService;
    // @Autowired
    // private PortfolioService portfolioService;
    // @Autowired
    // private PortfolioRepository portfolioRepository;
    // @Autowired
    // private AccountRepository accountRepository;
    // @Autowired
    // private OrderService orderService;

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    private final String engineName;

    public MatchingEngine(String engineName) {
        this.engineName = engineName;
    }


    public void match(OrderMessage orderMessage) {
        OrderBook orderBook = orderBooks.computeIfAbsent(orderMessage.getStockSymbol(), k -> new OrderBook(k));
        MatchResult matchResult = orderBook.match(orderMessage);


        System.out.println("Match result for order: " + orderMessage);
        System.out.println(matchResult);
        // TODO: send match result to mq to save it
        // for (TradeMessage tradeMessage : matchResult.getTrades()) {
        //     System.out.println(tradeMessage);
        // }
        // for (OrderMessage order : matchResult.getFilledOrders()) {
        //     System.out.println(order);
        // }
        // System.out.println("Partially fiiled: " + matchResult.getPartiallyFilledOrder());
    }

    public OrderBook getOrderBook(String stockSymbol) {
        return orderBooks.get(stockSymbol);
    }

    public OrderBook getOrCreateOrderBook(String stockSymbol) {
        return orderBooks.computeIfAbsent(stockSymbol, k -> new OrderBook(stockSymbol));
    }
}
