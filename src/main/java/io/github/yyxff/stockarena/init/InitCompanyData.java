package io.github.yyxff.stockarena.init;

import io.github.yyxff.stockarena.common.IdGenerator;
import io.github.yyxff.stockarena.model.*;
import io.github.yyxff.stockarena.repository.AccountRepository;
import io.github.yyxff.stockarena.repository.OrderRepository;
import io.github.yyxff.stockarena.repository.PortfolioRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class InitCompanyData {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    @Transactional
    public void init() {

        Optional<Account> existingCompany = accountRepository.findById(1L);

        if (existingCompany.isPresent()) {
            System.out.println("Company account already exists. Skipping initialization.");
            return;
        }

        // 1. Create Company Account
        Account acc = new Account();
        // acc.setId(1L);
        // acc.setType("COMPANY");
        acc.setAvailableBalance(BigDecimal.valueOf(1000000));
        acc.setFrozenBalance(BigDecimal.ZERO);
        accountRepository.save(acc);

        // 2. Create initial portfolio for the company
        String stockSymbol = "AAPL";
        Portfolio portfolio = new Portfolio();
        portfolio.setAccountId(acc.getId());
        portfolio.setStockSymbol(stockSymbol);
        portfolio.setAvailableShares(10000);
        portfolio.setFrozenShares(0);
        portfolioRepository.save(portfolio);

        // 3. Create initial sell orders
        int sellQuantity = 1000;
        BigDecimal sellPrice = BigDecimal.valueOf(150);

        if (sellQuantity <= portfolio.getAvailableShares()) {
            portfolio.setAvailableShares(portfolio.getAvailableShares() - sellQuantity);
            portfolio.setFrozenShares(portfolio.getFrozenShares() + sellQuantity);
            portfolioRepository.save(portfolio);

            Order order = new Order();
            order.setId(idGenerator.nextId());
            order.setAccountId(acc.getId());
            order.setStockSymbol(stockSymbol);
            order.setOrderType(OrderType.SELL);
            order.setPrice(sellPrice);
            order.setTotalQuantity(sellQuantity);
            order.setRemainingQuantity(sellQuantity);
            order.setStatus(OrderStatus.OPEN);
            orderRepository.save(order);
        }
    }
}