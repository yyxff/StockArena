package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.common.IdGenerator;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.matching.dto.MatchResult;
import io.github.yyxff.stockarena.model.BalanceChangeType;
import io.github.yyxff.stockarena.model.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderBookTest {

    @Mock
    private IdGenerator idGenerator;

    private OrderBook orderBook;
    private final AtomicLong idSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        when(idGenerator.nextId()).thenAnswer(inv -> idSeq.getAndIncrement());
        orderBook = new OrderBook("AAPL", idGenerator);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OrderMessage buy(long orderId, long accountId, String price, int qty) {
        return order(orderId, accountId, OrderType.BUY, price, qty);
    }

    private OrderMessage sell(long orderId, long accountId, String price, int qty) {
        return order(orderId, accountId, OrderType.SELL, price, qty);
    }

    private OrderMessage order(long orderId, long accountId, OrderType type, String price, int qty) {
        OrderMessage msg = new OrderMessage();
        msg.setOrderId(orderId);
        msg.setAccountId(accountId);
        msg.setStockSymbol("AAPL");
        msg.setOrderType(type);
        msg.setPrice(new BigDecimal(price));
        msg.setTotalQuantity(qty);
        msg.setRemainingQuantity(qty);
        msg.setCreatedAt(LocalDateTime.now());
        return msg;
    }

    // -------------------------------------------------------------------------
    // No match
    // -------------------------------------------------------------------------

    @Test
    void buyOrderNotMatchedWhenNoBetterSell() {
        MatchResult result = orderBook.match(buy(1, 100, "99.00", 10));

        assertThat(result.getTradeWithChanges()).isEmpty();
        assertThat(result.getFilledOrders()).isEmpty();
    }

    @Test
    void sellOrderNotMatchedWhenNoBetterBuy() {
        MatchResult result = orderBook.match(sell(1, 200, "101.00", 10));

        assertThat(result.getTradeWithChanges()).isEmpty();
        assertThat(result.getFilledOrders()).isEmpty();
    }

    @Test
    void noMatchWhenSellPriceAboveBuyPrice() {
        orderBook.match(buy(1, 100, "99.00", 10));
        MatchResult result = orderBook.match(sell(2, 200, "100.00", 10));

        assertThat(result.getTradeWithChanges()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Full match
    // -------------------------------------------------------------------------

    @Test
    void buyAndSellAtSamePriceBothFilled() {
        orderBook.match(sell(1, 200, "100.00", 10));
        MatchResult result = orderBook.match(buy(2, 100, "100.00", 10));

        assertThat(result.getTradeWithChanges()).hasSize(1);
        assertThat(result.getFilledOrders()).hasSize(2);
        assertThat(result.getPartiallyFilledOrder()).isNull();

        var trade = result.getTradeWithChanges().get(0).getTrade();
        assertThat(trade.getQuantity()).isEqualTo(10);
        assertThat(trade.getPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void tradeAtSellPriceWhenBuyComesIn() {
        // Sell at 100, buy at 105 → trade at sell price (100)
        orderBook.match(sell(1, 200, "100.00", 10));
        MatchResult result = orderBook.match(buy(2, 100, "105.00", 10));

        var trade = result.getTradeWithChanges().get(0).getTrade();
        assertThat(trade.getPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void tradeAtBuyPriceWhenSellComesIn() {
        // Buy at 105, sell at 100 → trade at buy price (105)
        orderBook.match(buy(1, 100, "105.00", 10));
        MatchResult result = orderBook.match(sell(2, 200, "100.00", 10));

        var trade = result.getTradeWithChanges().get(0).getTrade();
        assertThat(trade.getPrice()).isEqualByComparingTo("105.00");
    }

    // -------------------------------------------------------------------------
    // Partial fill
    // -------------------------------------------------------------------------

    @Test
    void buyPartiallyFilledWhenSellQuantitySmaller() {
        orderBook.match(sell(1, 200, "100.00", 6));
        MatchResult result = orderBook.match(buy(2, 100, "100.00", 10));

        assertThat(result.getTradeWithChanges()).hasSize(1);
        assertThat(result.getTradeWithChanges().get(0).getTrade().getQuantity()).isEqualTo(6);
        // sell order (id=1) is fully filled; buy order (id=2) still has 4 remaining
        assertThat(result.getFilledOrders()).extracting(OrderMessage::getOrderId).containsExactly(1L);
        assertThat(result.getPartiallyFilledOrder().getOrderId()).isEqualTo(2L);
        assertThat(result.getPartiallyFilledOrder().getRemainingQuantity()).isEqualTo(4);
    }

    @Test
    void sellPartiallyFilledWhenBuyQuantitySmaller() {
        orderBook.match(buy(1, 100, "100.00", 6));
        MatchResult result = orderBook.match(sell(2, 200, "100.00", 10));

        assertThat(result.getTradeWithChanges()).hasSize(1);
        assertThat(result.getTradeWithChanges().get(0).getTrade().getQuantity()).isEqualTo(6);
        assertThat(result.getFilledOrders()).extracting(OrderMessage::getOrderId).containsExactlyInAnyOrder(1L);
        assertThat(result.getPartiallyFilledOrder().getOrderId()).isEqualTo(2L);
        assertThat(result.getPartiallyFilledOrder().getRemainingQuantity()).isEqualTo(4);
    }

    // -------------------------------------------------------------------------
    // Multiple matches
    // -------------------------------------------------------------------------

    @Test
    void buyOrderMatchesMultipleSellOrders() {
        orderBook.match(sell(1, 201, "100.00", 5));
        orderBook.match(sell(2, 202, "100.00", 5));
        MatchResult result = orderBook.match(buy(3, 100, "100.00", 10));

        assertThat(result.getTradeWithChanges()).hasSize(2);
        assertThat(result.getFilledOrders()).hasSize(3); // 2 sells + 1 buy
        assertThat(result.getPartiallyFilledOrder()).isNull();
    }

    @Test
    void sellOrderMatchesMultipleBuyOrders() {
        orderBook.match(buy(1, 101, "100.00", 5));
        orderBook.match(buy(2, 102, "100.00", 5));
        MatchResult result = orderBook.match(sell(3, 200, "100.00", 10));

        assertThat(result.getTradeWithChanges()).hasSize(2);
        assertThat(result.getFilledOrders()).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // Price priority
    // -------------------------------------------------------------------------

    @Test
    void bestSellPriceMatchedFirst() {
        // Two sell orders: 102 and 100 — lower price should match first
        orderBook.match(sell(1, 201, "102.00", 10));
        orderBook.match(sell(2, 202, "100.00", 10));
        MatchResult result = orderBook.match(buy(3, 100, "105.00", 10));

        var trade = result.getTradeWithChanges().get(0).getTrade();
        assertThat(trade.getPrice()).isEqualByComparingTo("100.00");
        assertThat(trade.getSellOrderId()).isEqualTo(2L);
    }

    @Test
    void bestBuyPriceMatchedFirst() {
        // Two buy orders: 98 and 105 — higher price should match first
        orderBook.match(buy(1, 101, "98.00", 10));
        orderBook.match(buy(2, 102, "105.00", 10));
        MatchResult result = orderBook.match(sell(3, 200, "95.00", 10));

        var trade = result.getTradeWithChanges().get(0).getTrade();
        assertThat(trade.getPrice()).isEqualByComparingTo("105.00");
        assertThat(trade.getBuyOrderId()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // Balance changes
    // -------------------------------------------------------------------------

    @Test
    void buyerGetsDeductAndRefundWhenBidAboveTradePrice() {
        // Sell at 100, buy at 110 → trade at 100, buyer refunded 10 per share
        orderBook.match(sell(1, 200, "100.00", 10));
        MatchResult result = orderBook.match(buy(2, 100, "110.00", 10));

        var changes = result.getTradeWithChanges().get(0).getBalanceChanges();

        assertThat(changes).anySatisfy(c -> {
            assertThat(c.getChangeType()).isEqualTo(BalanceChangeType.TRADE_DEDUCT);
            assertThat(c.getAmount()).isEqualByComparingTo("1000.00"); // 100 * 10
            assertThat(c.getAccountId()).isEqualTo(100L);
        });
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.getChangeType()).isEqualTo(BalanceChangeType.TRADE_REFUND);
            assertThat(c.getAmount()).isEqualByComparingTo("100.00"); // (110-100) * 10
            assertThat(c.getAccountId()).isEqualTo(100L);
        });
    }

    @Test
    void noRefundWhenBidEqualsTradePrice() {
        orderBook.match(sell(1, 200, "100.00", 10));
        MatchResult result = orderBook.match(buy(2, 100, "100.00", 10));

        var changes = result.getTradeWithChanges().get(0).getBalanceChanges();

        assertThat(changes).noneMatch(c -> c.getChangeType() == BalanceChangeType.TRADE_REFUND);
    }

    @Test
    void sellerAlwaysGetsTradeAdd() {
        orderBook.match(sell(1, 200, "100.00", 10));
        MatchResult result = orderBook.match(buy(2, 100, "100.00", 10));

        var changes = result.getTradeWithChanges().get(0).getBalanceChanges();

        assertThat(changes).anySatisfy(c -> {
            assertThat(c.getChangeType()).isEqualTo(BalanceChangeType.TRADE_ADD);
            assertThat(c.getAmount()).isEqualByComparingTo("1000.00");
            assertThat(c.getAccountId()).isEqualTo(200L);
        });
    }
}
