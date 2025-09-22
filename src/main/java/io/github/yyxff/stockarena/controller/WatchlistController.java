package io.github.yyxff.stockarena.controller;

import io.github.yyxff.stockarena.model.Watchlist;
import io.github.yyxff.stockarena.service.WatchlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /**
     * Get the watchlist for a specific account
     * example: GET /api/watchlist?accountId=1
     */
    @GetMapping
    public ResponseEntity<List<Watchlist>> getWatchlist(@RequestParam Long accountId) {
        return ResponseEntity.ok(watchlistService.getWatchlist(accountId));
    }

    /**
     * Add to watchlist
     * example: POST /api/watchlist/add?accountId=1&symbol=AAPL
     */
    @PostMapping("/add")
    public ResponseEntity<Watchlist> addToWatchlist(
            @RequestParam Long accountId,
            @RequestParam String symbol) {
        return ResponseEntity.ok(watchlistService.addToWatchlist(accountId, symbol));
    }

    /**
     * Remove from watchlist
     * example: DELETE /api/watchlist/remove?accountId=1&symbol=AAPL
     */
    @DeleteMapping("/remove")
    public ResponseEntity<Void> removeFromWatchlist(
            @RequestParam Long accountId,
            @RequestParam String symbol) {
        watchlistService.removeFromWatchlist(accountId, symbol);
        return ResponseEntity.noContent().build();
    }
}