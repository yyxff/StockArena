package io.github.yyxff.stockarena.matching.producer;

import io.github.yyxff.stockarena.config.KafkaTopics;
import io.github.yyxff.stockarena.dto.TradeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TradeProducer {

    @Autowired
    private KafkaTemplate kafkaTemplate;

    public void sendTrade(TradeMessage trade) {
        kafkaTemplate.send(KafkaTopics.TRADES, trade.getStockSymbol(), trade);
        System.out.println("Produced trade to Kafka: " + trade);
    }
}