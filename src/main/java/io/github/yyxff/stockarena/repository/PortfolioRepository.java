package io.github.yyxff.stockarena.repository;

import io.github.yyxff.stockarena.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    Optional<Portfolio> findByAccountIdAndStockSymbol(Long accountId, String stockSymbol);

}
