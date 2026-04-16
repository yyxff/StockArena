package io.github.yyxff.stockarena.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    private Long id;

    @Column(nullable = false)
    private String stockSymbol;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long buyOrderId;

    @Column(nullable = false)
    private long sellOrderId;

    @Column(name = "created_at")
    private long createdAt = System.currentTimeMillis();
}
