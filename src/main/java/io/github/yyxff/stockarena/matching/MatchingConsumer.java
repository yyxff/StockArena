package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import org.apache.kafka.common.PartitionInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.apache.kafka.clients.consumer.Consumer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class MatchingConsumer {

    @Autowired
    private MatchingEngineManager matchingEngineManager;

    private MatchingEngine matchingEngine;


    @KafkaListener(topics = "orders", groupId = "order-matcher")
    public void consume(OrderMessage orderMessage, Consumer<?, ?> consumer) {
        if (matchingEngine == null) {
            List<PartitionInfo> partitions = consumer.partitionsFor("orders");
            int partition = getKafkaPartition(orderMessage.getStockSymbol(), partitions.size());
            matchingEngine = matchingEngineManager.getEngineByPartition(partition);
            System.out.println("assign engine " + partition);
        }
        matchingEngine.match(orderMessage);
    }

    private int getKafkaPartition(String key, int partitionCount) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        return org.apache.kafka.common.utils.Utils.toPositive(
                org.apache.kafka.common.utils.Utils.murmur2(keyBytes)
        ) % partitionCount;
    }
}
