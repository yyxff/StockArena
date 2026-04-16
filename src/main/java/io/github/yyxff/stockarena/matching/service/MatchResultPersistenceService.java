package io.github.yyxff.stockarena.matching.service;

import io.github.yyxff.stockarena.dto.BalanceChangeDTO;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.dto.TradeWithChanges;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.model.BalanceChange;
import io.github.yyxff.stockarena.model.OrderStatus;
import io.github.yyxff.stockarena.model.Trade;
import io.github.yyxff.stockarena.repository.BalanceChangeRepository;
import io.github.yyxff.stockarena.repository.OrderRepository;
import io.github.yyxff.stockarena.repository.TradeRepository;
import io.github.yyxff.stockarena.service.AccountService;
import io.github.yyxff.stockarena.service.PortfolioService;
import io.github.yyxff.stockarena.service.RedisInventoryService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class MatchResultPersistenceService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private BalanceChangeRepository balanceChangeRepository;

    @Autowired
    private RedisInventoryService redisInventoryService;

    /**
     * Persist all trade records contained in the match result.
     * Idempotent: skips trades whose ID already exists in the database.
     * This is the critical step — the MQ message is acknowledged after this returns.
     */
    @Transactional
    public void saveTrades(MatchResult result) {
        for (TradeWithChanges twc : result.getTradeWithChanges()) {
            TradeMessage msg = twc.getTrade();
            if (!tradeRepository.existsById(msg.getId())) {
                tradeRepository.save(toTradeEntity(msg));
                log.info("Trade saved: id={} symbol={} price={} qty={}",
                        msg.getId(), msg.getStockSymbol(), msg.getPrice(), msg.getQuantity());
            }
        }
    }

    /**
     * Apply secondary DB updates: portfolio changes, balance changes, and order status.
     * Idempotent per trade: if BalanceChange records for a tradeId already exist the
     * trade's updates are skipped.  Throws on failure so the caller retries until every
     * trade is fully applied.
     *
     * Redis is intentionally excluded here.  Mixing Redis ops inside a @Transactional
     * method is unsafe: if the DB transaction rolls back, Redis cannot be rolled back,
     * and the next retry would double-apply the Redis increment.
     */
    @Transactional
    public void applySecondaryUpdatesDB(MatchResult result) {
        for (TradeWithChanges twc : result.getTradeWithChanges()) {
            TradeMessage trade = twc.getTrade();

            // Idempotency guard: skip if this trade's balance changes are already persisted
            if (balanceChangeRepository.existsByTradeId(trade.getId())) {
                log.info("Secondary DB updates for trade {} already applied, skipping", trade.getId());
                continue;
            }

            // Buyer receives shares
            portfolioService.addShares(trade.getBuyerAccountId(), trade.getStockSymbol(), trade.getQuantity());

            // Seller's frozen shares are deducted — Redis was already decremented at order placement
            portfolioService.deductShares(trade.getSellerAccountId(), trade.getStockSymbol(), trade.getQuantity());

            for (BalanceChangeDTO dto : twc.getBalanceChanges()) {
                balanceChangeRepository.save(toBalanceChangeEntity(dto));
                switch (dto.getChangeType()) {
                    case TRADE_ADD, DEPOSIT ->
                            accountService.addBalance(dto.getAccountId(), dto.getAmount());
                    case TRADE_DEDUCT ->
                            // Deducts from frozen balance — Redis available was already decremented at order placement
                            accountService.deductBalance(dto.getAccountId(), dto.getAmount());
                    case TRADE_REFUND, ORDER_CANCEL ->
                            accountService.releaseBalance(dto.getAccountId(), dto.getAmount());
                }
            }
        }

        // Order status updates are inherently idempotent (setting the same status twice is safe)
        for (OrderMessage order : result.getFilledOrders()) {
            orderRepository.findById(order.getOrderId()).ifPresent(o -> {
                o.setStatus(OrderStatus.FILLED);
                o.setRemainingQuantity(0);
                orderRepository.save(o);
            });
        }

        if (result.getPartiallyFilledOrder() != null) {
            OrderMessage partial = result.getPartiallyFilledOrder();
            orderRepository.findById(partial.getOrderId()).ifPresent(o -> {
                o.setRemainingQuantity(partial.getRemainingQuantity());
                o.setStatus(OrderStatus.PARTIALLY_FILLED);
                orderRepository.save(o);
            });
        }
    }

    /**
     * Apply Redis inventory updates after the DB transaction has committed.
     * Called only after {@link #applySecondaryUpdatesDB} returns successfully.
     *
     * Redis is an approximate cache — if this call fails the inventory will
     * self-heal on the next order placement via {@code syncFromDB}.  Therefore
     * failures here are logged but not retried indefinitely.
     */
    public void applySecondaryUpdatesRedis(MatchResult result) {
        for (TradeWithChanges twc : result.getTradeWithChanges()) {
            TradeMessage trade = twc.getTrade();

            // Buyer receives shares
            redisInventoryService.addShares(trade.getBuyerAccountId(), trade.getStockSymbol(), trade.getQuantity());

            for (BalanceChangeDTO dto : twc.getBalanceChanges()) {
                switch (dto.getChangeType()) {
                    case TRADE_ADD, DEPOSIT ->
                            // Seller (or deposit recipient) gains available balance
                            redisInventoryService.addBalance(dto.getAccountId(), dto.getAmount());
                    case TRADE_REFUND, ORDER_CANCEL ->
                            // Buyer gets price-difference refund back to available balance
                            redisInventoryService.addBalance(dto.getAccountId(), dto.getAmount());
                    case TRADE_DEDUCT -> {
                        // No Redis action: available balance was already decremented at order placement
                    }
                }
            }
        }
    }

    private Trade toTradeEntity(TradeMessage msg) {
        Trade t = new Trade();
        t.setId(msg.getId());
        t.setBuyOrderId(msg.getBuyOrderId());
        t.setSellOrderId(msg.getSellOrderId());
        t.setStockSymbol(msg.getStockSymbol());
        t.setPrice(msg.getPrice());
        t.setQuantity(msg.getQuantity());
        t.setCreatedAt(msg.getCreatedAt());
        return t;
    }

    private BalanceChange toBalanceChangeEntity(BalanceChangeDTO dto) {
        BalanceChange bc = new BalanceChange();
        bc.setAccountId(dto.getAccountId());
        bc.setOrderId(dto.getOrderId());
        bc.setTradeId(dto.getTradeId());
        bc.setChangeType(dto.getChangeType());
        bc.setAmount(dto.getAmount());
        bc.setCreatedAt(LocalDateTime.now());
        return bc;
    }
}
