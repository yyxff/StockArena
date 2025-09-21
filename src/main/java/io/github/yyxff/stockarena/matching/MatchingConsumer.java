package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.KafkaHeaders;


@Component
public class MatchingConsumer {

    @Autowired
    private MatchingEngineManager matchingEngineManager;

    /**
     * Consume order messages from Kafka and dispatch to the appropriate MatchingEngine based on partition.
     * For instance: Partition 0 -> MatchingEngine 0
     * Because engine 0 will recover and process all orders belonging to partition 0
     * @param orderMessage
     * @param partition
     */
    @KafkaListener(topics = "orders", groupId = "order-matcher", concurrency = "4")
    public void consume(OrderMessage orderMessage, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        MatchingEngine engine = matchingEngineManager.getEngineByPartition(partition);
        System.out.println("assign to engine " + partition);
        engine.dispatch(orderMessage);
    }
}
