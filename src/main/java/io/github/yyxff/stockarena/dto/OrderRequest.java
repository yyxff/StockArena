package io.github.yyxff.stockarena.dto;

import io.github.yyxff.stockarena.model.OrderType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class OrderRequest {
    private Long accountId;
    private String stockSymbol;
    private int quantity;
    private BigDecimal price;
    private OrderType orderType;
    private String token;
}
