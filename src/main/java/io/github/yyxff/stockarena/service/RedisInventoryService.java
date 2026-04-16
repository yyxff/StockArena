package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.repository.AccountRepository;
import io.github.yyxff.stockarena.repository.PortfolioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Redis-backed inventory cache.
 *
 * Redis holds the "authorized quota" for each account's available balance and
 * available shares — initialised to 100% of the DB value on first access.
 * It is used as a fast pre-check before the real DB freeze; the DB remains the
 * source of truth.
 *
 * Key scheme:
 *   account:balance:{accountId}          → available balance (decimal string)
 *   portfolio:shares:{accountId}:{symbol} → available shares (integer string)
 */
@Service
public class RedisInventoryService {

    static final String BALANCE_KEY_PREFIX = "account:balance:";
    static final String SHARES_KEY_PREFIX  = "portfolio:shares:";

    /**
     * Increments a decimal key by the given amount using INCRBYFLOAT with the
     * plain-string representation of the value, bypassing Java's double conversion.
     *
     * Spring's RedisTemplate.increment(key, double) calls INCRBYFLOAT but converts
     * BigDecimal → double first, which can introduce floating-point error for values
     * like 0.1 or 0.3.  Passing toPlainString() as a Lua ARGV keeps the exact
     * decimal string all the way to the Redis command.
     */
    private static final RedisScript<String> INCR_BY_DECIMAL = RedisScript.of(
            "return redis.call('INCRBYFLOAT', KEYS[1], ARGV[1])",
            String.class
    );

    /**
     * Atomically checks whether the current value is >= required, and if so
     * subtracts it.  Returns:
     *  1  – success (deducted)
     *  0  – insufficient
     * -1  – key does not exist (Redis not yet initialised for this entry)
     */
    private static final RedisScript<Long> CHECK_AND_DEDUCT = RedisScript.of(
            "local cur = tonumber(redis.call('GET', KEYS[1])) " +
            "if cur == nil then return -1 end " +
            "local req = tonumber(ARGV[1]) " +
            "if cur < req then return 0 end " +
            "redis.call('SET', KEYS[1], tostring(cur - req)) " +
            "return 1",
            Long.class
    );

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    // -------------------------------------------------------------------------
    // Check-and-deduct (atomic, used at order placement)
    // -------------------------------------------------------------------------

    /**
     * Returns 1 (ok), 0 (insufficient), or -1 (not initialised).
     */
    public int checkAndDeductBalance(Long accountId, BigDecimal amount) {
        Long r = redisTemplate.execute(CHECK_AND_DEDUCT,
                List.of(BALANCE_KEY_PREFIX + accountId),
                amount.toPlainString());
        return r == null ? -1 : r.intValue();
    }

    /**
     * Returns 1 (ok), 0 (insufficient), or -1 (not initialised).
     */
    public int checkAndDeductShares(Long accountId, String symbol, int qty) {
        Long r = redisTemplate.execute(CHECK_AND_DEDUCT,
                List.of(SHARES_KEY_PREFIX + accountId + ":" + symbol),
                String.valueOf(qty));
        return r == null ? -1 : r.intValue();
    }

    // -------------------------------------------------------------------------
    // Sync from DB (used on initialisation and after DB freeze failure)
    // -------------------------------------------------------------------------

    public void syncBalanceFromDB(Long accountId) {
        BigDecimal available = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId))
                .getAvailableBalance();
        redisTemplate.opsForValue().set(BALANCE_KEY_PREFIX + accountId, available.toPlainString());
    }

    public void syncSharesFromDB(Long accountId, String symbol) {
        int available = portfolioRepository
                .findByAccountIdAndStockSymbol(accountId, symbol)
                .map(p -> p.getAvailableShares())
                .orElse(0);
        redisTemplate.opsForValue().set(
                SHARES_KEY_PREFIX + accountId + ":" + symbol,
                String.valueOf(available));
    }

    // -------------------------------------------------------------------------
    // Increment (used after a trade settles)
    // -------------------------------------------------------------------------

    /** Buyer receives shares after a trade. */
    public void addShares(Long accountId, String symbol, int qty) {
        redisTemplate.opsForValue().increment(
                SHARES_KEY_PREFIX + accountId + ":" + symbol, (long) qty);
    }

    /** Seller / buyer-refund receives balance after a trade. */
    public void addBalance(Long accountId, BigDecimal amount) {
        redisTemplate.execute(INCR_BY_DECIMAL,
                List.of(BALANCE_KEY_PREFIX + accountId),
                amount.toPlainString());
    }
}
