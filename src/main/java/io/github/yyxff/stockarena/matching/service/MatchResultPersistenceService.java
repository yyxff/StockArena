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
     * Apply secondary updates: portfolio changes, balance changes, and order status.
     * These run after the MQ ack. Failures here are logged but do not trigger a
     * message retry — eventual consistency is acceptable for these fields.
     */
    public void applySecondaryUpdates(MatchResult result) {
        applyPortfolioAndBalanceUpdates(result);
        updateOrderStatuses(result);
    }

    private void applyPortfolioAndBalanceUpdates(MatchResult result) {
        for (TradeWithChanges twc : result.getTradeWithChanges()) {
            TradeMessage trade = twc.getTrade();
            try {
                portfolioService.addShares(trade.getBuyerAccountId(), trade.getStockSymbol(), trade.getQuantity());
                portfolioService.deductShares(trade.getSellerAccountId(), trade.getStockSymbol(), trade.getQuantity());

                for (BalanceChangeDTO dto : twc.getBalanceChanges()) {
                    balanceChangeRepository.save(toBalanceChangeEntity(dto));
                    switch (dto.getChangeType()) {
                        case TRADE_ADD, DEPOSIT ->
                                accountService.addBalance(dto.getAccountId(), dto.getAmount());
                        case TRADE_DEDUCT ->
                                accountService.deductBalance(dto.getAccountId(), dto.getAmount());
                        case TRADE_REFUND, ORDER_CANCEL ->
                                accountService.releaseBalance(dto.getAccountId(), dto.getAmount());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to apply portfolio/balance updates for trade {}", trade.getId(), e);
            }
        }
    }

    private void updateOrderStatuses(MatchResult result) {
        for (OrderMessage order : result.getFilledOrders()) {
            try {
                orderRepository.findById(order.getOrderId()).ifPresent(o -> {
                    o.setStatus(OrderStatus.FILLED);
                    o.setRemainingQuantity(0);
                    orderRepository.save(o);
                });
            } catch (Exception e) {
                log.error("Failed to update filled order status for order {}", order.getOrderId(), e);
            }
        }

        if (result.getPartiallyFilledOrder() != null) {
            OrderMessage partial = result.getPartiallyFilledOrder();
            try {
                orderRepository.findById(partial.getOrderId()).ifPresent(o -> {
                    o.setRemainingQuantity(partial.getRemainingQuantity());
                    o.setStatus(OrderStatus.PARTIALLY_FILLED);
                    orderRepository.save(o);
                });
            } catch (Exception e) {
                log.error("Failed to update partially filled order status for order {}", partial.getOrderId(), e);
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
