package io.github.yyxff.stockarena.matching.service;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.MatchingWorker;
import io.github.yyxff.stockarena.matching.producer.TradeProducer;

import java.util.ArrayList;
import java.util.List;

public class MatchingEngine {

    private final String engineName;

    private final int workerCount;

    /**
     * List of workers processing orders
     * We manually manage threads here instead of threads pool
     * Because we have self-defined dispatch logic: hash by stock symbol
     */
    private final List<MatchingWorker> workers;


    public MatchingEngine(String engineName, int workerCount, PersistenceService persistenceService, TradeProducer tradeProducer) {
        this.engineName = engineName;
        this.workerCount = workerCount;
        this.workers = new ArrayList<>();
        for (int i = 0; i < workerCount; i++) {
            MatchingWorker worker = new MatchingWorker(
                    "MatchingWorker-" + engineName + "-" + i
                    ,persistenceService
                    ,tradeProducer);
            workers.add(worker);
            new Thread(worker).start();
        }
    }

    /**
     * Dispatch order to a corresponding worker based on stock symbol hash
     * So that same stock orders are always processed by the same worker in order
     * @param orderMessage
     */
    public void dispatch(OrderMessage orderMessage) {
        int workerIndex = Math.abs(orderMessage.getStockSymbol().hashCode()) % workerCount;
        workers.get(workerIndex).submit(orderMessage);
    }

    /**
     * Send init order message to the corresponding worker to restore order book to memory
     * @param orderMessage
     */
    public void initOrder(OrderMessage orderMessage) {
        int workerIndex = Math.abs(orderMessage.getStockSymbol().hashCode()) % workerCount;
        workers.get(workerIndex).initOrder(orderMessage);
    }
}
