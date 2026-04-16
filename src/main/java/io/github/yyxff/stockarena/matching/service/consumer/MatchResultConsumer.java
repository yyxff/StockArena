package io.github.yyxff.stockarena.matching.service.consumer;

import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.matching.service.MatchResultPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RocketMQMessageListener(
        topic = "match-result-topic",
        consumerGroup = "match-result-persistence"
)
public class MatchResultConsumer implements RocketMQListener<MatchResult> {

    @Autowired
    private MatchResultPersistenceService matchResultPersistenceService;

    @Override
    public void onMessage(MatchResult matchResult) {
        // Step 1: save trade records — throws on failure so RocketMQ retries
        matchResultPersistenceService.saveTrades(matchResult);

        // MQ ack happens when onMessage returns normally (after step 1).
        // Step 2: secondary updates — portfolio, balance, order status.
        // Failures are caught and logged inside; they do not trigger a retry.
        matchResultPersistenceService.applySecondaryUpdates(matchResult);
    }
}
