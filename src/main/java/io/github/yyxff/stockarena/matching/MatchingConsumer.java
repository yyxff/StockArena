package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.service.MatchingService;
import org.hibernate.annotations.NaturalId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MatchingConsumer {

    @Autowired
    private MatchingService matchingService;


    @KafkaListener(topics = "orders", groupId = "order-matcher")
    public void consume(OrderMessage orderMessage) {
        matchingService.match(orderMessage);
    }
}
