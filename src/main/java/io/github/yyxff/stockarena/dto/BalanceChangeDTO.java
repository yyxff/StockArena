package io.github.yyxff.stockarena.dto;

import io.github.yyxff.stockarena.model.BalanceChange;
import io.github.yyxff.stockarena.model.BalanceChangeType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class BalanceChangeDTO {
    private Long id;
    private Long accountId;
    private Long orderId;
    private Long tradeId;
    private BalanceChangeType changeType; // e.g., "DEPOSIT", "WITHDRAWAL", "TRADE"
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
