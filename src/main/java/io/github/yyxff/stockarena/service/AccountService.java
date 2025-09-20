package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.model.Account;
import io.github.yyxff.stockarena.repository.AccountRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.BiConsumer;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    private static final int MAX_RETRIES = 3;


    public Account getAccountById(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account "+accountId+" not found"));
    }

    public BigDecimal getAvailableBalance(Long accountId) {
        Account account = getAccountById(accountId);
        return account.getAvailableBalance();
    }

    @Transactional
    public void releaseBalance(Long accountId, BigDecimal amount) {
        modifyBalance(accountId, amount, (account, amt) -> {
            if (account.getFrozenBalance().compareTo(amt) < 0) {
                throw new IllegalArgumentException("Insufficient frozen balance");
            }
            account.setAvailableBalance(account.getAvailableBalance().add(amt));
            account.setFrozenBalance(account.getFrozenBalance().subtract(amt));
        });
    }

    @Transactional
    public void freezeBalance(Long accountId, BigDecimal amount) {
        modifyBalance(accountId, amount, (account, amt) -> {
            if (account.getAvailableBalance().compareTo(amt) < 0) {
                throw new IllegalArgumentException("Insufficient available balance");
            }
            account.setAvailableBalance(account.getAvailableBalance().subtract(amt));
            account.setFrozenBalance(account.getFrozenBalance().add(amt));
        });
    }

    @Transactional
    public void addBalance(Long accountId, BigDecimal amount) {
        modifyBalance(accountId, amount, (account, amt) -> {
            account.setAvailableBalance(account.getAvailableBalance().add(amt));
        });
    }

    @Transactional
    public void deductBalance(Long accountId, BigDecimal amount) {
        modifyBalance(accountId, amount, (account, amt) -> {
            if (account.getFrozenBalance().compareTo(amt) < 0) {
                throw new IllegalArgumentException("Insufficient frozen balance");
            }
            account.setFrozenBalance(account.getFrozenBalance().subtract(amt));
        });
    }

    private void modifyBalance(Long accountId, BigDecimal amount, BiConsumer<Account, BigDecimal> balanceUpdater) {
        Account account = getAccountById(accountId);

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                balanceUpdater.accept(account, amount);
                accountRepository.save(account);
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
