package io.github.yyxff.stockarena.matching;

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

    private final Map<Integer, MatchingEngine> engines = new HashMap<>();

    private final int partitionCount = 4;

    @PostConstruct
    private void initEngines() {
        for (int i = 0; i < partitionCount; i++) {
            engines.put(i, new MatchingEngine("Engine-" + i, 4, matchResultPersistenceService, tradeProducer, matchResultProducer));
        }
        System.out.println("Initialed " + partitionCount + " MatchingEngines");
    }

    public MatchingEngine getEngineByPartition(int partition) {
        return engines.get(partition);
    }
}