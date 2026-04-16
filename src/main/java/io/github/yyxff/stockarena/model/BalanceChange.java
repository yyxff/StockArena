package io.github.yyxff.stockarena.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "balance_changes", indexes = {
        @Index(name = "idx_balance_change_trade", columnList = "tradeId")
})
public class BalanceChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column
    private Long orderId;

    @Column
    private Long tradeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BalanceChangeType changeType;

    @Column(nullable = false)
    private java.math.BigDecimal amount;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
