package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.model.Portfolio;
import io.github.yyxff.stockarena.repository.PortfolioRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

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
    public void deductShares(Long accountId, String stockSymbol, int shares) {
        Portfolio portfolio = portfolioRepository
                .findByAccountIdAndStockSymbol(accountId, stockSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                if (portfolio.getAvailableShares() < 0) {
                    throw new IllegalArgumentException("Insufficient available shares");
                }

                portfolio.setAvailableShares(portfolio.getAvailableShares() - shares);
                portfolio.setFrozenShares(portfolio.getFrozenShares() + shares);

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
