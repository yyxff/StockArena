package io.github.yyxff.stockarena.service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.yyxff.stockarena.config.KafkaTopics;
import io.github.yyxff.stockarena.dto.KLineDTO;
import io.github.yyxff.stockarena.dto.KLineMessage;
import io.github.yyxff.stockarena.model.KLine;
import io.github.yyxff.stockarena.repository.KLineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * K线数据持久化消费者
 * 消费来自 kline-updates topic 的完成K线数据，并将其保存到数据库
 * 实现基于 symbol + timestamp 的幂等性设计
 */
@Component
public class KLinePersistenceConsumer {

    private final KLineRepository kLineRepository;

    @Autowired
    public KLinePersistenceConsumer(KLineRepository kLineRepository) {
        this.kLineRepository = kLineRepository;
    }

    @KafkaListener(
            topics = KafkaTopics.KLINE_UPDATES,
            groupId = "kline-persistence",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeCompletedKLine(KLineMessage updateMessage) {
        try {
            // 处理接收到的完成K线数据
            processCompletedKLine(updateMessage);

        } catch (Exception e) {
            System.err.println("Error processing completed KLine: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理完成的K线数据 - 幂等性保存到数据库
     */
    private void processCompletedKLine(KLineMessage updateMessage) throws JsonProcessingException {
        String symbol = updateMessage.getSymbol();
        KLineDTO dtoKline = updateMessage.getKlineData();
        String interval = updateMessage.getInterval();

        System.out.println("Received completed KLine for persistence: " +
                          "symbol=" + symbol +
                          ", interval=" + interval +
                          ", timestamp=" + new Date(dtoKline.getTimestamp()));

        // 幂等性检查：基于 symbol + timestamp 检查是否已存在
        if (kLineRepository.existsBySymbolAndTimestamp(symbol, dtoKline.getTimestamp())) {
            System.out.println("KLine already exists for symbol=" + symbol +
                             ", timestamp=" + new Date(dtoKline.getTimestamp()) +
                             " - skipping duplicate insert (idempotent behavior)");
            return;
        }

        // 创建数据库实体
        KLine entity = convertToEntity(symbol, dtoKline);

        try {
            // 保存到数据库
            KLine savedEntity = kLineRepository.save(entity);
            System.out.println("Successfully saved KLine to database: " +
                             "id=" + savedEntity.getId() +
                             ", symbol=" + symbol +
                             ", timestamp=" + new Date(dtoKline.getTimestamp()));

        } catch (Exception e) {
            handleDatabaseException(e, symbol, dtoKline.getTimestamp());
        }
    }

    /**
     * 转换DTO到数据库实体
     */
    private KLine convertToEntity(String symbol, KLineDTO dtoKline) {
        KLine entity = new KLine();
        entity.setSymbol(symbol);
        entity.setTimestamp(dtoKline.getTimestamp());
        entity.setOpen(dtoKline.getOpen());
        entity.setClose(dtoKline.getClose());
        entity.setHigh(dtoKline.getHigh());
        entity.setLow(dtoKline.getLow());
        entity.setVolume(dtoKline.getVolume());
        return entity;
    }

    /**
     * 处理数据库异常，特别是并发插入时的唯一约束违反
     */
    private void handleDatabaseException(Exception e, String symbol, Long timestamp) {
        String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (errorMsg.contains("constraint") ||
            errorMsg.contains("duplicate") ||
            errorMsg.contains("unique")) {
            // 并发插入导致的重复，这是正常的幂等性行为
            System.out.println("KLine insertion failed due to duplicate key (concurrent insert detected) - " +
                             "idempotent behavior working correctly: " +
                             "symbol=" + symbol + ", timestamp=" + new Date(timestamp));
        } else {
            // 其他数据库异常需要重新抛出
            System.err.println("Failed to save KLine to database: " + e.getMessage());
            throw new RuntimeException("Database error while saving KLine for symbol=" + symbol, e);
        }
    }
}
