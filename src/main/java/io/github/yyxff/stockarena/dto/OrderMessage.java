package io.github.yyxff.stockarena.dto;

import io.github.yyxff.stockarena.model.OrderStatus;
import io.github.yyxff.stockarena.model.OrderType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class OrderMessage {
    private Long orderId;
    private Long accountId;
    private String stockSymbol;
    private int totalQuantity;
    private int remainingQuantity;
    private OrderType orderType;
    private BigDecimal price;
    private OrderStatus orderStatus;
    private LocalDateTime createdAt;
}
