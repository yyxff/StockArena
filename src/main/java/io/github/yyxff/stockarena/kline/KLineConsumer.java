package io.github.yyxff.stockarena.kline;

import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.service.KLineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KLineConsumer {

    private final KLineService kLineService;

    @Autowired
    public KLineConsumer(KLineService kLineService) {
        this.kLineService = kLineService;
    }

    @KafkaListener(
            topics = "trades",             // Trade topic
            groupId = "kline-generator",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(TradeMessage tradeMessage) {
        // Update K-Line data in memory
        kLineService.updateCurrentKLine(tradeMessage);
    }
}
