package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.model.Order;
import io.github.yyxff.stockarena.model.OrderType;
import lombok.Data;

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
        MatchResult matchResult = new MatchResult(new ArrayList<>(), new ArrayList<>());
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
                // Match found
                int tradeQuantity = Math.min(buyOrder.getRemainingQuantity(), bestSellOrder.getRemainingQuantity());
                // Create trade
                TradeMessage trade = new TradeMessage();
                trade.setStockSymbol(buyOrder.getStockSymbol());
                trade.setPrice(bestSellOrder.getPrice()); // Trade at sell price
                trade.setQuantity(tradeQuantity);
                trade.setBuyOrderId(buyOrder.getOrderId());
                trade.setSellOrderId(bestSellOrder.getOrderId());
                trade.setCreatedAt(java.time.LocalDateTime.now());
                // Add trade to result
                matchResult.getTrades().add(trade);

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
                // Match found
                int tradeQuantity = Math.min(sellOrder.getRemainingQuantity(), bestBuyOrder.getRemainingQuantity());
                // Create trade
                TradeMessage trade = new TradeMessage();
                trade.setStockSymbol(sellOrder.getStockSymbol());
                trade.setPrice(bestBuyOrder.getPrice()); // Trade at buy price
                trade.setQuantity(tradeQuantity);
                trade.setBuyOrderId(bestBuyOrder.getOrderId());
                trade.setSellOrderId(sellOrder.getOrderId());
                trade.setCreatedAt(java.time.LocalDateTime.now());
                // Add trade to result
                matchResult.getTrades().add(trade);

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
