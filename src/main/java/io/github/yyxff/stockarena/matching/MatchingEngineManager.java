package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.common.IdGenerator;
import io.github.yyxff.stockarena.matching.producer.MatchResultProducer;
import io.github.yyxff.stockarena.matching.producer.TradeProducer;
import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import io.github.yyxff.stockarena.matching.service.MatchResultPersistenceService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MatchingEngineManager {

    @Autowired
    private MatchResultPersistenceService matchResultPersistenceService;

    @Autowired
    private TradeProducer tradeProducer;

    @Autowired
    private MatchResultProducer matchResultProducer;

    @Autowired
    private IdGenerator idGenerator;

    private final Map<Integer, MatchingEngine> engines = new HashMap<>();

    private final int engineCount = 4;

    /**
     * Set to true by MatchingInit after all historical orders have been loaded
     * into the order books.  MatchingConsumer checks this before dispatching;
     * messages that arrive before init completes are rejected so RocketMQ retries
     * them, preventing orders from landing in an empty order book.
     */
    private volatile boolean initialized = false;

    @PostConstruct
    private void initEngines() {
        for (int i = 0; i < engineCount; i++) {
            engines.put(i, new MatchingEngine(
                    "Engine-" + i, 4,
                    idGenerator,
                    matchResultPersistenceService,
                    tradeProducer,
                    matchResultProducer));
        }
        System.out.println("Initialised " + engineCount + " MatchingEngines");
    }

    /**
     * Route an order to the correct engine by stock symbol.
     * Same symbol always maps to the same engine (and from there to the same worker).
     */
    public MatchingEngine getEngineBySymbol(String symbol) {
        int index = (symbol.hashCode() & Integer.MAX_VALUE) % engineCount;
        return engines.get(index);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        initialized = true;
    }
}
