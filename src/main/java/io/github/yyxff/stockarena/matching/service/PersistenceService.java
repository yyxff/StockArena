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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PersistenceService {

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

    @Async("asyncExecutor")
    @Transactional
    public void saveMatchResult(MatchResult result) {
        // 1. Save trade records and update portfolios
        for (TradeWithChanges tradeWithChanges : result.getTradeWithChanges()) {
            // Save trade record
            TradeMessage trade = tradeWithChanges.getTrade();
            Trade tradeEntity = tradeRepository.save(toTradeEntity(trade));
            System.out.println("Trade saved: " + trade);

            // Update buyer and seller portfolios
            BigDecimal totalPrice = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
            portfolioService.addShares(trade.getBuyerAccountId(), trade.getStockSymbol(), trade.getQuantity());
            portfolioService.deductShares(trade.getSellerAccountId(), trade.getStockSymbol(), trade.getQuantity());

            // Save and apply balance changes
            for (BalanceChangeDTO balanceChange : tradeWithChanges.getBalanceChanges()) {
                balanceChange.setTradeId(tradeEntity.getId());
                balanceChangeRepository.save(toBalanceChangeEntity(balanceChange));
                switch (balanceChange.getChangeType()) {
                    case TRADE_ADD, DEPOSIT -> {
                        accountService.addBalance(balanceChange.getAccountId(), balanceChange.getAmount());
                    }
                    case TRADE_DEDUCT -> {
                        accountService.deductBalance(balanceChange.getAccountId(), balanceChange.getAmount());
                    }
                    case TRADE_REFUND, ORDER_CANCEL -> {
                        accountService.releaseBalance(balanceChange.getAccountId(), balanceChange.getAmount());
                    }
                }
            }
        }


        // 2. Update filled orders
        for (OrderMessage order : result.getFilledOrders()) {
            orderRepository.findById(order.getOrderId()).ifPresent(o -> {
                o.setStatus(OrderStatus.FILLED);
                o.setRemainingQuantity(0);
                orderRepository.save(o);
            });
        }

        // 3. Update partially filled order
        if (result.getPartiallyFilledOrder() != null) {
            OrderMessage partial = result.getPartiallyFilledOrder();
            orderRepository.findById(partial.getOrderId()).ifPresent(o -> {
                o.setRemainingQuantity(partial.getRemainingQuantity());
                o.setStatus(OrderStatus.PARTIALLY_FILLED);
                orderRepository.save(o);
            });
        }
    }

    private Trade toTradeEntity(TradeMessage msg) {
        Trade t = new Trade();
        t.setBuyOrderId(msg.getBuyOrderId());
        t.setSellOrderId(msg.getSellOrderId());
        t.setStockSymbol(msg.getStockSymbol());
        t.setPrice(msg.getPrice());
        t.setQuantity(msg.getQuantity());
        t.setCreatedAt(LocalDateTime.now());
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