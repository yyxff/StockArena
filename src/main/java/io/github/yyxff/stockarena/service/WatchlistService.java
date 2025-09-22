package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.model.Account;
import io.github.yyxff.stockarena.model.Watchlist;
import io.github.yyxff.stockarena.repository.AccountRepository;
import io.github.yyxff.stockarena.repository.WatchlistRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final AccountRepository accountRepository;

    public WatchlistService(WatchlistRepository watchlistRepository, AccountRepository accountRepository) {
        this.watchlistRepository = watchlistRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Get the watchlist for a specific account
     */
    public List<Watchlist> getWatchlist(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return watchlistRepository.findByAccount(account);
    }

    /**
     * Add a stock to the watchlist
     */
    public Watchlist addToWatchlist(Long accountId, String symbol) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        return watchlistRepository.findByAccountAndSymbol(account, symbol)
                .orElseGet(() -> {
                    Watchlist entry = new Watchlist();
                    entry.setAccount(account);
                    entry.setSymbol(symbol);
                    return watchlistRepository.save(entry);
                });
    }

    /**
     * Remove a stock from the watchlist
     */
    public void removeFromWatchlist(Long accountId, String symbol) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        watchlistRepository.deleteByAccountAndSymbol(account, symbol);
    }
}