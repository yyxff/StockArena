package io.github.yyxff.stockarena.repository;

import io.github.yyxff.stockarena.model.Account;
import io.github.yyxff.stockarena.model.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByAccount(Account account);

    Optional<Watchlist> findByAccountAndSymbol(Account account, String symbol);

    void deleteByAccountAndSymbol(Account account, String symbol);
}
