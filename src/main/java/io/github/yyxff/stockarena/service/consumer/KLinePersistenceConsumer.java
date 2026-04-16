package io.github.yyxff.stockarena.service.consumer;

import io.github.yyxff.stockarena.dto.KLineDTO;
import io.github.yyxff.stockarena.dto.KLineMessage;
import io.github.yyxff.stockarena.model.KLine;
import io.github.yyxff.stockarena.repository.KLineRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "kline-topic",
        consumerGroup = "kline-persistence"
)
public class KLinePersistenceConsumer implements RocketMQListener<KLineMessage> {

    @Autowired
    private KLineRepository kLineRepository;

    @Override
    @Transactional
    public void onMessage(KLineMessage updateMessage) {
        String symbol = updateMessage.getSymbol();
        KLineDTO dto = updateMessage.getKlineData();

        if (kLineRepository.existsBySymbolAndTimestamp(symbol, dto.getTimestamp())) {
            log.debug("KLine already exists for symbol={}, timestamp={}, skipping", symbol, dto.getTimestamp());
            return;
        }

        try {
            kLineRepository.save(convertToEntity(symbol, dto));
            log.info("KLine saved: symbol={}, timestamp={}", symbol, dto.getTimestamp());
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert — idempotent behaviour, safe to ignore
            log.debug("KLine duplicate on concurrent insert for symbol={}, timestamp={}", symbol, dto.getTimestamp());
        }
    }

    private KLine convertToEntity(String symbol, KLineDTO dto) {
        KLine entity = new KLine();
        entity.setSymbol(symbol);
        entity.setTimestamp(dto.getTimestamp());
        entity.setOpen(dto.getOpen());
        entity.setClose(dto.getClose());
        entity.setHigh(dto.getHigh());
        entity.setLow(dto.getLow());
        entity.setVolume(dto.getVolume());
        return entity;
    }
}
