package io.github.yyxff.stockarena.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String stockSymbol;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int remainingQuantity;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType; // "BUY" or "SELL"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status; // "OPEN", "PARTIALLY_FILLED", "FILLED", "CANCELLED"

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
