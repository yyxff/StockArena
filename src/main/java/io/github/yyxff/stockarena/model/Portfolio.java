package io.github.yyxff.stockarena.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "portfolios", indexes = {
        @Index(name = "idx_portfolio_account_symbol", columnList = "accountId, stockSymbol", unique = true)
})
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String stockSymbol;

    @Column(nullable = false)
    private int availableShares;

    @Column(nullable = false)
    private int frozenShares;

    @Version
    private Long version;
}
