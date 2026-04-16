package io.github.yyxff.stockarena.matching.producer;

import io.github.yyxff.stockarena.dto.TradeMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TradeProducer {

    private static final String TRADE_TOPIC = "trade-topic";

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public void sendTrade(TradeMessage trade) {
        rocketMQTemplate.convertAndSend(TRADE_TOPIC, trade);
    }
}
