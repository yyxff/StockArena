package io.github.yyxff.stockarena.matching.service.consumer;

import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.matching.service.MatchResultPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Slf4j
@Service
@RocketMQMessageListener(
        topic = "match-result-topic",
        consumerGroup = "match-result-persistence"
)
public class MatchResultConsumer implements RocketMQListener<MatchResult> {

    @Autowired
    private MatchResultPersistenceService matchResultPersistenceService;

    @Autowired
    @Qualifier("secondaryUpdateExecutor")
    private Executor secondaryUpdateExecutor;

    @Override
    public void onMessage(MatchResult matchResult) {
        // Step 1: save trade records — idempotent, throws on failure so RocketMQ retries
        matchResultPersistenceService.saveTrades(matchResult);

        // Return here → RocketMQ acks the message.
        // Step 2: portfolio, balance, and order status updates run asynchronously.
        // The dedicated thread retries with backoff until success; failures here
        // are transient (DB unavailability etc.) and do not affect message consumption.
        secondaryUpdateExecutor.execute(() -> retrySecondaryUpdates(matchResult));
    }

    private void retrySecondaryUpdates(MatchResult matchResult) {
        int attempt = 0;
        while (true) {
            try {
                matchResultPersistenceService.applySecondaryUpdates(matchResult);
                return;
            } catch (Exception e) {
                attempt++;
                long delayMs = Math.min(1_000L * attempt, 30_000L);
                log.warn("Secondary updates failed (attempt {}), retrying in {}ms", attempt, delayMs, e);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Secondary update retry interrupted for match result", ie);
                    return;
                }
            }
        }
    }
}
