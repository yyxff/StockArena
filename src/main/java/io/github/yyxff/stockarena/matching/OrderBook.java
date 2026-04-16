package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.common.IdGenerator;
import io.github.yyxff.stockarena.dto.BalanceChangeDTO;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.dto.TradeWithChanges;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.model.BalanceChangeType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.PriorityQueue;


@Data
public class OrderBook {

    private final String stockSymbol;

    private final IdGenerator idGenerator;

    // Buy orders: highest price first, then earliest time first
    private final PriorityQueue<OrderMessage> buyOrders =
            new PriorityQueue<>(Comparator
                    .comparing(OrderMessage::getPrice).reversed()
                    .thenComparing(OrderMessage::getCreatedAt)
            );

    // Sell orders: lowest price first, then earliest time first
    private final PriorityQueue<OrderMessage> sellOrders =
            new PriorityQueue<>(Comparator
                    .comparing(OrderMessage::getPrice)
                    .thenComparing(OrderMessage::getCreatedAt)
            );

    public OrderBook(String stockSymbol, IdGenerator idGenerator) {
        this.stockSymbol = stockSymbol;
        this.idGenerator = idGenerator;
    }

    public MatchResult match(OrderMessage orderMessage) {
        MatchResult matchResult = new MatchResult();
        if (orderMessage.getOrderType() == io.github.yyxff.stockarena.model.OrderType.BUY) {
            matchBuyOrder(orderMessage, matchResult);
            if (orderMessage.getRemainingQuantity() > 0) {
                buyOrders.offer(orderMessage);
            }
        } else {
            matchSellOrder(orderMessage, matchResult);
            if (orderMessage.getRemainingQuantity() > 0) {
                sellOrders.offer(orderMessage);
            }
        }
        return matchResult;
    }

    private TradeMessage buildTrade(OrderMessage buyOrder, OrderMessage sellOrder,
                                    int tradeQuantity, BigDecimal tradePrice) {
        TradeMessage trade = new TradeMessage();
        trade.setId(idGenerator.nextId());
        trade.setStockSymbol(buyOrder.getStockSymbol());
        trade.setPrice(tradePrice);
        trade.setQuantity(tradeQuantity);
        trade.setBuyerAccountId(buyOrder.getAccountId());
        trade.setSellerAccountId(sellOrder.getAccountId());
        trade.setBuyOrderId(buyOrder.getOrderId());
        trade.setSellOrderId(sellOrder.getOrderId());
        trade.setCreatedAt(System.currentTimeMillis());
        return trade;
    }

    private void matchBuyOrder(OrderMessage buyOrder, MatchResult matchResult) {
        while (buyOrder.getRemainingQuantity() > 0 && !sellOrders.isEmpty()) {
            OrderMessage bestSellOrder = sellOrders.peek();
            if (bestSellOrder.getPrice().compareTo(buyOrder.getPrice()) > 0) {
                break;
            }

            int tradeQuantity = Math.min(buyOrder.getRemainingQuantity(), bestSellOrder.getRemainingQuantity());
            BigDecimal tradePrice = bestSellOrder.getPrice(); // trade at sell price
            BigDecimal totalPrice = tradePrice.multiply(BigDecimal.valueOf(tradeQuantity));

            TradeMessage trade = buildTrade(buyOrder, bestSellOrder, tradeQuantity, tradePrice);
            TradeWithChanges tradeWithChanges = new TradeWithChanges();
            tradeWithChanges.setTrade(trade);

            // Buyer: deduct frozen balance
            BalanceChangeDTO buyerDeduction = new BalanceChangeDTO();
            buyerDeduction.setAccountId(buyOrder.getAccountId());
            buyerDeduction.setOrderId(buyOrder.getOrderId());
            buyerDeduction.setTradeId(trade.getId());
            buyerDeduction.setChangeType(BalanceChangeType.TRADE_DEDUCT);
            buyerDeduction.setAmount(totalPrice);
            tradeWithChanges.getBalanceChanges().add(buyerDeduction);

            // Buyer: refund difference if bid price > trade price
            if (buyOrder.getPrice().compareTo(tradePrice) > 0) {
                BalanceChangeDTO buyerRefund = new BalanceChangeDTO();
                buyerRefund.setAccountId(buyOrder.getAccountId());
                buyerRefund.setOrderId(buyOrder.getOrderId());
                buyerRefund.setTradeId(trade.getId());
                buyerRefund.setChangeType(BalanceChangeType.TRADE_REFUND);
                buyerRefund.setAmount(buyOrder.getPrice()
                        .multiply(BigDecimal.valueOf(tradeQuantity))
                        .subtract(totalPrice));
                tradeWithChanges.getBalanceChanges().add(buyerRefund);
            }

            // Seller: add to available balance
            BalanceChangeDTO sellerAddition = new BalanceChangeDTO();
            sellerAddition.setAccountId(bestSellOrder.getAccountId());
            sellerAddition.setOrderId(bestSellOrder.getOrderId());
            sellerAddition.setTradeId(trade.getId());
            sellerAddition.setChangeType(BalanceChangeType.TRADE_ADD);
            sellerAddition.setAmount(totalPrice);
            tradeWithChanges.getBalanceChanges().add(sellerAddition);

            matchResult.getTradeWithChanges().add(tradeWithChanges);

            buyOrder.setRemainingQuantity(buyOrder.getRemainingQuantity() - tradeQuantity);
            bestSellOrder.setRemainingQuantity(bestSellOrder.getRemainingQuantity() - tradeQuantity);

            if (bestSellOrder.getRemainingQuantity() == 0 && buyOrder.getRemainingQuantity() == 0) {
                matchResult.getFilledOrders().add(bestSellOrder);
                matchResult.getFilledOrders().add(buyOrder);
                matchResult.setPartiallyFilledOrder(null);
                sellOrders.poll();
                break;
            } else if (buyOrder.getRemainingQuantity() == 0) {
                matchResult.getFilledOrders().add(buyOrder);
                matchResult.setPartiallyFilledOrder(bestSellOrder);
                break;
            } else {
                matchResult.getFilledOrders().add(bestSellOrder);
                matchResult.setPartiallyFilledOrder(buyOrder);
                sellOrders.poll();
            }
        }
    }

    private void matchSellOrder(OrderMessage sellOrder, MatchResult matchResult) {
        while (sellOrder.getRemainingQuantity() > 0 && !buyOrders.isEmpty()) {
            OrderMessage bestBuyOrder = buyOrders.peek();
            if (bestBuyOrder.getPrice().compareTo(sellOrder.getPrice()) < 0) {
                break;
            }

            int tradeQuantity = Math.min(sellOrder.getRemainingQuantity(), bestBuyOrder.getRemainingQuantity());
            BigDecimal tradePrice = bestBuyOrder.getPrice(); // trade at buy price
            BigDecimal totalPrice = tradePrice.multiply(BigDecimal.valueOf(tradeQuantity));

            TradeMessage trade = buildTrade(bestBuyOrder, sellOrder, tradeQuantity, tradePrice);
            TradeWithChanges tradeWithChanges = new TradeWithChanges();
            tradeWithChanges.setTrade(trade);

            // Buyer: deduct frozen balance
            BalanceChangeDTO buyerDeduction = new BalanceChangeDTO();
            buyerDeduction.setAccountId(bestBuyOrder.getAccountId());
            buyerDeduction.setOrderId(bestBuyOrder.getOrderId());
            buyerDeduction.setTradeId(trade.getId());
            buyerDeduction.setChangeType(BalanceChangeType.TRADE_DEDUCT);
            buyerDeduction.setAmount(totalPrice);
            tradeWithChanges.getBalanceChanges().add(buyerDeduction);

            // Seller: add to available balance
            BalanceChangeDTO sellerAddition = new BalanceChangeDTO();
            sellerAddition.setAccountId(sellOrder.getAccountId());
            sellerAddition.setOrderId(sellOrder.getOrderId());
            sellerAddition.setTradeId(trade.getId());
            sellerAddition.setChangeType(BalanceChangeType.TRADE_ADD);
            sellerAddition.setAmount(totalPrice);
            tradeWithChanges.getBalanceChanges().add(sellerAddition);

            matchResult.getTradeWithChanges().add(tradeWithChanges);

            sellOrder.setRemainingQuantity(sellOrder.getRemainingQuantity() - tradeQuantity);
            bestBuyOrder.setRemainingQuantity(bestBuyOrder.getRemainingQuantity() - tradeQuantity);

            if (bestBuyOrder.getRemainingQuantity() == 0 && sellOrder.getRemainingQuantity() == 0) {
                matchResult.getFilledOrders().add(bestBuyOrder);
                matchResult.getFilledOrders().add(sellOrder);
                matchResult.setPartiallyFilledOrder(null);
                buyOrders.poll();
                break;
            } else if (sellOrder.getRemainingQuantity() == 0) {
                matchResult.getFilledOrders().add(sellOrder);
                matchResult.setPartiallyFilledOrder(bestBuyOrder);
                break;
            } else {
                matchResult.getFilledOrders().add(bestBuyOrder);
                matchResult.setPartiallyFilledOrder(sellOrder);
                buyOrders.poll();
            }
        }
    }
}
