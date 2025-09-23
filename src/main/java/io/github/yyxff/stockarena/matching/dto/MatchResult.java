package io.github.yyxff.stockarena.matching.dto;

import io.github.yyxff.stockarena.dto.BalanceChangeDTO;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.dto.TradeWithChanges;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.Trade;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class MatchResult {
    private final List<TradeWithChanges> tradeWithChanges;
    private final List<OrderMessage> filledOrders;
    private OrderMessage partiallyFilledOrder;

    public MatchResult() {
        this.tradeWithChanges = new ArrayList<>();
        this.filledOrders = new ArrayList<>();
    }
}
