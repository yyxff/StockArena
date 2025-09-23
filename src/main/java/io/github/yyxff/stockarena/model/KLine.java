package io.github.yyxff.stockarena.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "klines",
       uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "timestamp"}))
public class KLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private Long timestamp;

    @Column
    private java.math.BigDecimal open;

    @Column
    private java.math.BigDecimal high;

    @Column
    private java.math.BigDecimal low;

    @Column
    private java.math.BigDecimal close;

    @Column
    private Long volume;
}
