package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MatchingConsumer {

    @Autowired
    private ObjectProvider<MatchingEngine> engineProvider;

    private MatchingEngine matchingEngine;


    @KafkaListener(topics = "orders", groupId = "order-matcher")
    public void consume(OrderMessage orderMessage) {
        if (matchingEngine == null) {
            matchingEngine = engineProvider.getObject();
        }
        matchingEngine.match(orderMessage);
    }
}
