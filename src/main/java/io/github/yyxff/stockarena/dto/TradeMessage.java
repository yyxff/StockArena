package io.github.yyxff.stockarena.dto;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class TradeMessage {
    private Long id;
    private String stockSymbol;
    private BigDecimal price;
    private int quantity;
    private long buyerAccountId;
    private long sellerAccountId;
    private long buyOrderId;
    private long sellOrderId;
    private long createdAt;
}
