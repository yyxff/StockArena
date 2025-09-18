package io.github.yyxff.stockarena.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "portfolios")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String stockSymbol;

    @Column(nullable = false)
    private int shares;

    @Column(nullable = false)
    private int frozenShares;
}
