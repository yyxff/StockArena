package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.model.Account;
import io.github.yyxff.stockarena.repository.AccountRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    private static final int MAX_RETRIES = 3;


    public Account getAccountById(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    public BigDecimal getAvailableBalance(Long accountId) {
        // Placeholder implementation
        return BigDecimal.valueOf(10000);
    }

    @Transactional
    public void deductBalance(Long accountId, BigDecimal amount) {
        Account account = getAccountById(accountId);

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                if (account.getAvailableBalance().compareTo(amount) < 0) {
                    throw new IllegalArgumentException("Insufficient balance");
                }

                account.setAvailableBalance(account.getAvailableBalance().subtract(amount));
                account.setFrozenBalance(account.getFrozenBalance().add(amount));

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
