package io.github.yyxff.stockarena.matching.producer;

import io.github.yyxff.stockarena.config.KafkaTopics;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MatchResultProducer {

    @Autowired
    private KafkaTemplate kafkaTemplate;

    public void sendMatchResult(MatchResult matchResult) {
        kafkaTemplate.send(KafkaTopics.MATCH_RESULTS, matchResult);
        System.out.println("Produced match result to Kafka: " + matchResult);
    }
}
