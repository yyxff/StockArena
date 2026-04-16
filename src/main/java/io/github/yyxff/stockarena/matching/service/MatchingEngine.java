package io.github.yyxff.stockarena.matching.service;

import io.github.yyxff.stockarena.common.IdGenerator;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.MatchingWorker;
import io.github.yyxff.stockarena.matching.producer.MatchResultProducer;
import io.github.yyxff.stockarena.matching.producer.TradeProducer;

import java.util.ArrayList;
import java.util.List;

public class MatchingEngine {

    private final String engineName;

    private final int workerCount;

    /**
     * List of workers processing orders.
     * Threads are managed manually so we can apply a consistent
     * dispatch strategy: hash by stock symbol.
     */
    private final List<MatchingWorker> workers;

    public MatchingEngine(String engineName,
                          int workerCount,
                          IdGenerator idGenerator,
                          MatchResultPersistenceService matchResultPersistenceService,
                          TradeProducer tradeProducer,
                          MatchResultProducer matchResultProducer) {
        this.engineName = engineName;
        this.workerCount = workerCount;
        this.workers = new ArrayList<>();
        for (int i = 0; i < workerCount; i++) {
            MatchingWorker worker = new MatchingWorker(
                    "MatchingWorker-" + engineName + "-" + i,
                    idGenerator,
                    matchResultPersistenceService,
                    tradeProducer,
                    matchResultProducer);
            workers.add(worker);
            new Thread(worker).start();
        }
    }

    /**
     * Dispatch order to the worker responsible for this stock symbol.
     * Same symbol always maps to the same worker, preserving per-symbol order.
     */
    public void dispatch(OrderMessage orderMessage) {
        int workerIndex = (orderMessage.getStockSymbol().hashCode() & Integer.MAX_VALUE) % workerCount;
        workers.get(workerIndex).submit(orderMessage);
    }

    /**
     * Recover an existing order from the database into the order book at startup.
     */
    public void initOrder(OrderMessage orderMessage) {
        int workerIndex = (orderMessage.getStockSymbol().hashCode() & Integer.MAX_VALUE) % workerCount;
        workers.get(workerIndex).initOrder(orderMessage);
    }
}
