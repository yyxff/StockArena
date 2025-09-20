package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.model.Portfolio;
import io.github.yyxff.stockarena.repository.PortfolioRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.BiConsumer;

@Service
public class PortfolioService {

    @Autowired
    private PortfolioRepository portfolioRepository;

    private static final int MAX_RETRIES = 3;

    public int getAvailableShares(Long accountId, String stockSymbol) {
        Optional<Portfolio> portfolioOpt = portfolioRepository.findByAccountIdAndStockSymbol(accountId, stockSymbol);
        return portfolioOpt.map(Portfolio::getAvailableShares).orElse(0);
    }

    @Transactional
    public Portfolio getOrCreatePortfolio(Long accountId, String stockSymbol) {
        return portfolioRepository.findByAccountIdAndStockSymbol(accountId, stockSymbol)
                .orElseGet(() -> {
                    Portfolio newPortfolio = new Portfolio();
                    newPortfolio.setAccountId(accountId);
                    newPortfolio.setStockSymbol(stockSymbol);
                    newPortfolio.setAvailableShares(0);
                    newPortfolio.setFrozenShares(0);
                    return portfolioRepository.save(newPortfolio);
                });
    }

    @Transactional
    public void addShares(Long accountId, String stockSymbol, int shares) {
        portfolioRepository.findByAccountIdAndStockSymbol(accountId, stockSymbol)
                .orElseGet(() -> {
                    Portfolio newPortfolio = new Portfolio();
                    newPortfolio.setAccountId(accountId);
                    newPortfolio.setStockSymbol(stockSymbol);
                    newPortfolio.setAvailableShares(0);
                    newPortfolio.setFrozenShares(0);
                    return portfolioRepository.save(newPortfolio);
                });
        modifyShares(accountId, stockSymbol, shares, (portfolio, sh) -> {
            portfolio.setAvailableShares(portfolio.getAvailableShares() + sh);
        });
    }

    @Transactional
    public void deductShares(Long accountId, String stockSymbol, int shares) {
        modifyShares(accountId, stockSymbol, shares, (portfolio, sh) -> {
            if (portfolio.getFrozenShares() < sh) {
                throw new IllegalArgumentException("Insufficient frozen shares: " + portfolio.getFrozenShares() + " requested: " + sh);
            }
            portfolio.setFrozenShares(portfolio.getFrozenShares() - sh);
        });
    }

    @Transactional
    public void freezeShares(Long accountId, String stockSymbol, int shares) {
        modifyShares(accountId, stockSymbol, shares, (portfolio, sh) -> {
            if (portfolio.getAvailableShares() < sh) {
                throw new IllegalArgumentException("Insufficient available shares");
            }
            portfolio.setAvailableShares(portfolio.getAvailableShares() - sh);
            portfolio.setFrozenShares(portfolio.getFrozenShares() + sh);
        });
    }

    @Transactional
    public void releaseShares(Long accountId, String stockSymbol, int shares) {
        modifyShares(accountId, stockSymbol, shares, (portfolio, sh) -> {
            if (portfolio.getFrozenShares() < sh) {
                throw new IllegalArgumentException("Insufficient frozen shares");
            }
            portfolio.setAvailableShares(portfolio.getAvailableShares() + sh);
            portfolio.setFrozenShares(portfolio.getFrozenShares() - sh);
        });
    }

    private void modifyShares(Long accountId, String stockSymbol, int shares, BiConsumer<Portfolio, Integer> portfolioUpdater) {
        Portfolio portfolio = portfolioRepository
                .findByAccountIdAndStockSymbol(accountId, stockSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                portfolioUpdater.accept(portfolio, shares);
                portfolioRepository.save(portfolio);
                return; // Success
            } catch (OptimisticLockException e) {
                if (i == MAX_RETRIES - 1) {
                    throw e; // Rethrow after max retries
                }
                // Log the exception and retry
            }
        }

    }
}
