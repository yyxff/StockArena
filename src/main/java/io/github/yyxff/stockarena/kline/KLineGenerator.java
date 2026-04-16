package io.github.yyxff.stockarena.kline;

import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.service.KLineService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Consumes trade messages and updates in-memory K-line data.
 * K-line data loss is acceptable — this consumer does not affect trade records.
 */
@Component
@RocketMQMessageListener(
        topic = "trade-topic",
        consumerGroup = "kline-generator"
)
public class KLineGenerator implements RocketMQListener<TradeMessage> {

    @Autowired
    private KLineService kLineService;

    @Override
    public void onMessage(TradeMessage tradeMessage) {
        kLineService.updateCurrentKLine(tradeMessage);
    }
}
