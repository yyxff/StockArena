package io.github.yyxff.stockarena.matching.service;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.model.OrderStatus;
import io.github.yyxff.stockarena.model.Trade;
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

    @Async("asyncExecutor")
    @Transactional
    public void saveMatchResult(MatchResult result) {
        // 1. 保存成交记录
        for (TradeMessage trade : result.getTrades()) {
            tradeRepository.save(toTradeEntity(trade));
            System.out.println("Trade saved: " + trade);

            // 买家
            BigDecimal totalPrice = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
            accountService.deductBalance(trade.getBuyerAccountId(), totalPrice);
            portfolioService.addShares(trade.getBuyerAccountId(), trade.getStockSymbol(), trade.getQuantity());

            // 卖家
            accountService.addBalance(trade.getSellerAccountId(), totalPrice);
            portfolioService.deductShares(trade.getSellerAccountId(), trade.getStockSymbol(), trade.getQuantity());
        }

        // 2. 更新订单状态
        for (OrderMessage order : result.getFilledOrders()) {
            orderRepository.findById(order.getOrderId()).ifPresent(o -> {
                o.setStatus(OrderStatus.FILLED);
                o.setRemainingQuantity(0);
                orderRepository.save(o);
            });
        }

        // 3. 更新部分成交订单
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
}