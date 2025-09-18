package io.github.yyxff.stockarena.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long buyOrderId;

    @Column(nullable = false)
    private long sellOrderId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    

}
