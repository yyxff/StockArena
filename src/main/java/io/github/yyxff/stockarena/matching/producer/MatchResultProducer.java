package io.github.yyxff.stockarena.matching.producer;

import io.github.yyxff.stockarena.matching.dto.MatchResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MatchResultProducer {

    private static final String MATCH_RESULT_TOPIC = "match-result-topic";

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public void sendMatchResult(MatchResult matchResult) {
        rocketMQTemplate.convertAndSend(MATCH_RESULT_TOPIC, matchResult);
    }
}
