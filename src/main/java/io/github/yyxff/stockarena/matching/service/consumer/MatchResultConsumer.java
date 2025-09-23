package io.github.yyxff.stockarena.matching.service.consumer;

import io.github.yyxff.stockarena.config.KafkaTopics;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.matching.service.MatchResultPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MatchResultConsumer {

    @Autowired
    private MatchResultPersistenceService matchResultPersistenceService;

    @KafkaListener(topics = KafkaTopics.MATCH_RESULTS, groupId = "match-result-persistence-group")
    public void handleMatchResult(MatchResult matchResult) {
        try {
            log.info("Processing match result with {} trades and {} filled orders",
                    matchResult.getTradeWithChanges().size(),
                    matchResult.getFilledOrders().size());

            // Save to db
            matchResultPersistenceService.saveMatchResult(matchResult);

            log.info("Match result processed successfully");

        } catch (Exception e) {
            log.error("Failed to process match result", e);
            throw e; // Rethrow exception to trigger message retry
        }
    }
}
