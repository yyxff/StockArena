package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.dto.BalanceChangeDTO;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.dto.TradeWithChanges;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.model.BalanceChangeType;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.OrderType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;


@Data
public class OrderBook {

    // Stock symbol for this order book
    private final String stockSymbol;

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

    public OrderBook(String stockSymbol) {
        this.stockSymbol = stockSymbol;
    }

    public MatchResult match(OrderMessage orderMessage) {
        MatchResult matchResult = new MatchResult();
        if (orderMessage.getOrderType() == OrderType.BUY) {
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

    private void matchBuyOrder(OrderMessage buyOrder, MatchResult matchResult) {
        while (buyOrder.getRemainingQuantity() > 0 && !sellOrders.isEmpty()) {
            OrderMessage bestSellOrder = sellOrders.peek();
            if (bestSellOrder.getPrice().compareTo(buyOrder.getPrice()) <= 0) {

                TradeWithChanges tradeWithChanges = new TradeWithChanges();
                // Match found
                int tradeQuantity = Math.min(buyOrder.getRemainingQuantity(), bestSellOrder.getRemainingQuantity());
                // Create trade
                TradeMessage trade = new TradeMessage();
                trade.setStockSymbol(buyOrder.getStockSymbol());
                trade.setPrice(bestSellOrder.getPrice()); // Trade at sell price
                trade.setQuantity(tradeQuantity);
                trade.setBuyerAccountId(buyOrder.getAccountId());
                trade.setSellerAccountId(bestSellOrder.getAccountId());
                trade.setBuyOrderId(buyOrder.getOrderId());
                trade.setSellOrderId(bestSellOrder.getOrderId());
                trade.setCreatedAt(System.currentTimeMillis());
                // Add trade to result
                tradeWithChanges.setTrade(trade);

                // Get total trade price
                BigDecimal totalPrice = trade.getPrice().multiply(BigDecimal.valueOf(tradeQuantity));
                // Create Balance Change Records
                // Buyer balance deduction
                BalanceChangeDTO buyerDeduction = new BalanceChangeDTO();
                buyerDeduction.setAccountId(buyOrder.getAccountId());
                buyerDeduction.setOrderId(buyOrder.getOrderId());
                buyerDeduction.setTradeId(trade.getId());
                buyerDeduction.setChangeType(BalanceChangeType.TRADE_DEDUCT);
                buyerDeduction.setAmount(totalPrice);
                tradeWithChanges.getBalanceChanges().add(buyerDeduction);
                // Buyer refund frozen balance if needed
                if (buyOrder.getPrice().compareTo(bestSellOrder.getPrice()) > 0) {
                    BalanceChangeDTO buyerRefund = new BalanceChangeDTO();
                    buyerRefund.setAccountId(buyOrder.getAccountId());
                    buyerRefund.setOrderId(buyOrder.getOrderId());
                    buyerRefund.setTradeId(trade.getId());
                    buyerRefund.setChangeType(BalanceChangeType.TRADE_REFUND);
                    buyerRefund.setAmount(buyOrder.getPrice().multiply(BigDecimal.valueOf(tradeQuantity)).subtract(totalPrice));
                    tradeWithChanges.getBalanceChanges().add(buyerRefund);
                }
                // Seller balance addition
                BalanceChangeDTO sellerAddition = new BalanceChangeDTO();
                sellerAddition.setAccountId(bestSellOrder.getAccountId());
                sellerAddition.setOrderId(bestSellOrder.getOrderId());
                sellerAddition.setTradeId(trade.getId());
                sellerAddition.setChangeType(BalanceChangeType.TRADE_ADD);
                sellerAddition.setAmount(totalPrice);
                tradeWithChanges.getBalanceChanges().add(sellerAddition);

                matchResult.getTradeWithChanges().add(tradeWithChanges);


                // Update quantities
                buyOrder.setRemainingQuantity(buyOrder.getRemainingQuantity() - tradeQuantity);
                bestSellOrder.setRemainingQuantity(bestSellOrder.getRemainingQuantity() - tradeQuantity);

                // Check if orders are fully filled
                if (bestSellOrder.getRemainingQuantity() == 0 && buyOrder.getRemainingQuantity() == 0) {
                    // a. Both orders fully filled
                    matchResult.getFilledOrders().add(bestSellOrder);
                    matchResult.getFilledOrders().add(buyOrder);
                    matchResult.setPartiallyFilledOrder(null);
                    sellOrders.poll(); // Remove from queue
                    break;
                } else if (buyOrder.getRemainingQuantity() == 0) {
                    // b. Only buy order fully filled
                    matchResult.getFilledOrders().add(buyOrder);
                    matchResult.setPartiallyFilledOrder(bestSellOrder);
                    break;
                } else {
                    // c. Only sell order fully filled
                    matchResult.getFilledOrders().add(bestSellOrder);
                    matchResult.setPartiallyFilledOrder(buyOrder);
                }
            } else {
                break; // No more matches possible
            }
        }
    }

    private void matchSellOrder(OrderMessage sellOrder, MatchResult matchResult) {
        while (sellOrder.getRemainingQuantity() > 0 && !buyOrders.isEmpty()) {
            OrderMessage bestBuyOrder = buyOrders.peek();
            if (bestBuyOrder.getPrice().compareTo(sellOrder.getPrice()) >= 0) {

                TradeWithChanges tradeWithChanges = new TradeWithChanges();
                // Match found
                int tradeQuantity = Math.min(sellOrder.getRemainingQuantity(), bestBuyOrder.getRemainingQuantity());
                // Create trade
                TradeMessage trade = new TradeMessage();
                trade.setStockSymbol(sellOrder.getStockSymbol());
                trade.setPrice(bestBuyOrder.getPrice()); // Trade at buy price
                trade.setQuantity(tradeQuantity);
                trade.setBuyerAccountId(bestBuyOrder.getAccountId());
                trade.setSellerAccountId(sellOrder.getAccountId());
                trade.setBuyOrderId(bestBuyOrder.getOrderId());
                trade.setSellOrderId(sellOrder.getOrderId());
                trade.setCreatedAt(System.currentTimeMillis());
                // Add trade to result
                tradeWithChanges.setTrade(trade);


                // Get total trade price
                BigDecimal totalPrice = trade.getPrice().multiply(BigDecimal.valueOf(tradeQuantity));
                // Create Balance Change Records
                // Buyer balance deduction
                BalanceChangeDTO buyerDeduction = new BalanceChangeDTO();
                buyerDeduction.setAccountId(bestBuyOrder.getAccountId());
                buyerDeduction.setOrderId(bestBuyOrder.getOrderId());
                buyerDeduction.setTradeId(trade.getId());
                buyerDeduction.setChangeType(BalanceChangeType.TRADE_DEDUCT);
                buyerDeduction.setAmount(totalPrice);
                tradeWithChanges.getBalanceChanges().add(buyerDeduction);
                // Seller balance addition
                BalanceChangeDTO sellerAddition = new BalanceChangeDTO();
                sellerAddition.setAccountId(sellOrder.getAccountId());
                sellerAddition.setOrderId(sellOrder.getOrderId());
                sellerAddition.setTradeId(trade.getId());
                sellerAddition.setChangeType(BalanceChangeType.TRADE_ADD);
                sellerAddition.setAmount(totalPrice);
                tradeWithChanges.getBalanceChanges().add(sellerAddition);
                matchResult.getTradeWithChanges().add(tradeWithChanges);

                // Update quantities
                sellOrder.setRemainingQuantity(sellOrder.getRemainingQuantity() - tradeQuantity);
                bestBuyOrder.setRemainingQuantity(bestBuyOrder.getRemainingQuantity() - tradeQuantity);

                // Check if orders are fully filled
                if (bestBuyOrder.getRemainingQuantity() == 0 && sellOrder.getRemainingQuantity() == 0) {
                    // a. Both orders fully filled
                    matchResult.getFilledOrders().add(bestBuyOrder);
                    matchResult.getFilledOrders().add(sellOrder);
                    matchResult.setPartiallyFilledOrder(null);
                    buyOrders.poll(); // Remove from queue
                    break;
                } else if (sellOrder.getRemainingQuantity() == 0) {
                    // b. Only sell order fully filled
                    matchResult.getFilledOrders().add(sellOrder);
                    matchResult.setPartiallyFilledOrder(bestBuyOrder);
                    break;
                } else {
                    // c. Only buy order fully filled
                    matchResult.getFilledOrders().add(bestBuyOrder);
                    matchResult.setPartiallyFilledOrder(sellOrder);
                }
            } else {
                break; // No more matches possible
            }
        }
    }
}
