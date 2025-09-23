package io.github.yyxff.stockarena.kline;

import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.service.KLineService;
import io.github.yyxff.stockarena.websocket.KlineWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * K-Line Generator
 * Consumer of MQ: topic "trades"
 * TODO:Producer to MQ: topic "kline-updates"
 *
 * Consumes trade messages from Kafka and updates K-Line data in memory.
 * The updated K-Line data can then be:
 * 1. Send to MQ for further processing
 * 2. Publish to redis pub/sub channel for real-time updates to clients.
 */
@Component
public class KLineGenerator {

    private final KLineService kLineService;

    @Autowired
    public KLineGenerator(KLineService kLineService) {
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
