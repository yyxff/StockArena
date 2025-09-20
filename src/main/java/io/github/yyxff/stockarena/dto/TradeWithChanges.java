package io.github.yyxff.stockarena.dto;

import io.github.yyxff.stockarena.model.BalanceChange;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class TradeWithChanges {
    private TradeMessage trade;
    private final List<BalanceChangeDTO> balanceChanges;
}
