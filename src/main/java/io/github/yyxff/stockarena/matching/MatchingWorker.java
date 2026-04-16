package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.dto.TradeWithChanges;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.matching.producer.MatchResultProducer;
import io.github.yyxff.stockarena.matching.producer.TradeProducer;
import io.github.yyxff.stockarena.matching.service.MatchResultPersistenceService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MatchingWorker implements Runnable {

    private final String name;

    private final BlockingQueue<OrderMessage> queue = new LinkedBlockingQueue<>();

    private final Map<String, OrderBook> books = new HashMap<>();

    private final MatchResultPersistenceService matchResultPersistenceService;

    private final TradeProducer tradeProducer;

    private final MatchResultProducer matchResultProducer;

    private final OrderDeduplicator deduplicator = new OrderDeduplicator();

    public MatchingWorker(String name,
                          MatchResultPersistenceService matchResultPersistenceService,
                          TradeProducer tradeProducer,
                          MatchResultProducer matchResultProducer) {
        this.name = name;
        this.matchResultPersistenceService = matchResultPersistenceService;
        this.tradeProducer = tradeProducer;
        this.matchResultProducer = matchResultProducer;
    }

    public void submit(OrderMessage orderMessage) {
        long orderId = orderMessage.getOrderId();
        if (deduplicator.isDuplicate(orderId)) {
            System.out.println("Duplicate order rejected: " + orderId);
            return;
        }
        deduplicator.markSeen(orderId);
        queue.offer(orderMessage);
    }

    /**
     * Recover an existing order from the database into the order book at startup.
     * Also marks the order as seen so that any delayed MQ delivery of the same
     * order is rejected as a duplicate.
     */
    public void initOrder(OrderMessage orderMessage) {
        deduplicator.markSeen(orderMessage.getOrderId());
        String stockSymbol = orderMessage.getStockSymbol();
        OrderBook book = books.computeIfAbsent(stockSymbol, k -> new OrderBook(stockSymbol));
        switch (orderMessage.getOrderType()) {
            case BUY -> book.getBuyOrders().offer(orderMessage);
            case SELL -> book.getSellOrders().offer(orderMessage);
        }
    }

    private void sendAllTradesToMQ(MatchResult result) {
        for (TradeWithChanges tradeWithChanges : result.getTradeWithChanges()) {
            TradeMessage trade = tradeWithChanges.getTrade();
            tradeProducer.sendTrade(trade);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                OrderMessage orderMessage = queue.take();

                OrderBook book = books.computeIfAbsent(orderMessage.getStockSymbol(), k -> new OrderBook(k));
                MatchResult result = book.match(orderMessage);

                sendAllTradesToMQ(result);
                matchResultProducer.sendMatchResult(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
