package io.github.yyxff.stockarena.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KLine {
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private long volume;
    private long timestamp;
}
