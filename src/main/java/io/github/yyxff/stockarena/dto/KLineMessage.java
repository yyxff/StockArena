package io.github.yyxff.stockarena.dto;

import lombok.Data;

/**
 * K Line Message
 * Represents a K-line (candlestick) data message for a specific stock symbol and time interval.
 */
@Data
public class KLineMessage {
    private String symbol;      // 股票代码
    private KLineDTO klineData;    // K线数据
    private String interval;    // 时间间隔（如1m, 5m等）
    private long completedAt;   // 完成时间戳

    public KLineMessage() {}

    public KLineMessage(String symbol, KLineDTO klineData, String interval) {
        this.symbol = symbol;
        this.klineData = klineData;
        this.interval = interval;
        this.completedAt = System.currentTimeMillis();
    }
}
