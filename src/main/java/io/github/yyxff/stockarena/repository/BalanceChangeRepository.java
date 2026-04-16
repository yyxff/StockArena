package io.github.yyxff.stockarena.repository;

import io.github.yyxff.stockarena.dto.BalanceChangeDTO;
import io.github.yyxff.stockarena.model.BalanceChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BalanceChangeRepository extends JpaRepository<BalanceChange, Long> {

    boolean existsByTradeId(Long tradeId);
}
