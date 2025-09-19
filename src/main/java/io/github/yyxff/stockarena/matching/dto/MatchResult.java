package io.github.yyxff.stockarena.matching.dto;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.Trade;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class MatchResult {
    private final List<TradeMessage> trades;
    private final List<OrderMessage> filledOrders;
    private OrderMessage partiallyFilledOrder;
}
