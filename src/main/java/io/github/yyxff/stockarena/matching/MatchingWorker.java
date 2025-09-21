package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.matching.service.PersistenceService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MatchingWorker implements Runnable {

    private final String name;
    private final BlockingQueue<OrderMessage> queue = new LinkedBlockingQueue<>();
    private final Map<String, OrderBook> books = new HashMap<>();
    private final PersistenceService persistenceService;

    public MatchingWorker(String name, PersistenceService persistenceService) {
        this.name = name;
        this.persistenceService = persistenceService;
    }

    public void submit(OrderMessage orderMessage) {
        queue.offer(orderMessage);
    }

    /**
     * Recover existing orders in database for a stock symbol into the order book
     * @param orderMessage
     */
    public void initOrder(OrderMessage orderMessage) {
        String stockSymbol = orderMessage.getStockSymbol();
        OrderBook book = books.computeIfAbsent(stockSymbol, k -> new OrderBook(stockSymbol));
        switch (orderMessage.getOrderType()) {
            case BUY -> book.getBuyOrders().offer(orderMessage);
            case SELL -> book.getSellOrders().offer(orderMessage);
        }
    }

    @Override
    public void run() {
        while (true) {
            try{
                // 1. Take order message from queue
                OrderMessage orderMessage = queue.take();
                // 2. Match order
                OrderBook book = books.computeIfAbsent(orderMessage.getStockSymbol(), k -> new OrderBook(k));
                MatchResult result = book.match(orderMessage);
                // 3. Async Persist match result
                persistenceService.saveMatchResult(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
