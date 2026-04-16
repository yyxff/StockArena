package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.common.IdGenerator;
import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.dto.OrderRequest;
import io.github.yyxff.stockarena.model.OrderStatus;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class OrderService {

    private static final String ORDER_TOPIC = "order-topic";
    private static final String TOKEN_KEY_PREFIX = "order:token:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    @Autowired
    private AccountService accountService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisInventoryService redisInventoryService;

    // -------------------------------------------------------------------------
    // Token
    // -------------------------------------------------------------------------

    public String generateOrderToken(Long accountId) {
        String token = String.valueOf(idGenerator.nextId());
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, String.valueOf(accountId), TOKEN_TTL);
        return token;
    }

    private void consumeToken(String token, Long accountId) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Order token is required");
        }
        String stored = redisTemplate.opsForValue().getAndDelete(TOKEN_KEY_PREFIX + token);
        if (stored == null) {
            throw new IllegalArgumentException("Order token is invalid, expired, or already used");
        }
        if (!stored.equals(String.valueOf(accountId))) {
            throw new IllegalArgumentException("Order token does not belong to this account");
        }
    }

    // -------------------------------------------------------------------------
    // Place order
    // -------------------------------------------------------------------------

    public void placeBuyOrder(OrderRequest req) {
        consumeToken(req.getToken(), req.getAccountId());
        validateBuyOrder(req);

        BigDecimal total = req.getPrice().multiply(BigDecimal.valueOf(req.getQuantity()));
        redisCheckAndDeductBalance(req.getAccountId(), total);

        OrderMessage orderMsg = buildOrderMessage(req);
        try {
            sendTransactionalOrder(orderMsg);
        } catch (Exception e) {
            // DB freeze failed — resync Redis to the actual DB available balance
            redisInventoryService.syncBalanceFromDB(req.getAccountId());
            throw e;
        }
    }

    public void placeSellOrder(OrderRequest req) {
        consumeToken(req.getToken(), req.getAccountId());
        validateSellOrder(req);

        redisCheckAndDeductShares(req.getAccountId(), req.getStockSymbol(), req.getQuantity());

        OrderMessage orderMsg = buildOrderMessage(req);
        try {
            sendTransactionalOrder(orderMsg);
        } catch (Exception e) {
            // DB freeze failed — resync Redis to the actual DB available shares
            redisInventoryService.syncSharesFromDB(req.getAccountId(), req.getStockSymbol());
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Redis inventory pre-check
    // -------------------------------------------------------------------------

    private void redisCheckAndDeductBalance(Long accountId, BigDecimal amount) {
        int result = redisInventoryService.checkAndDeductBalance(accountId, amount);
        if (result == -1) {
            // Key not initialised — load from DB and retry once
            redisInventoryService.syncBalanceFromDB(accountId);
            result = redisInventoryService.checkAndDeductBalance(accountId, amount);
        }
        if (result == 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }
    }

    private void redisCheckAndDeductShares(Long accountId, String symbol, int qty) {
        int result = redisInventoryService.checkAndDeductShares(accountId, symbol, qty);
        if (result == -1) {
            redisInventoryService.syncSharesFromDB(accountId, symbol);
            result = redisInventoryService.checkAndDeductShares(accountId, symbol, qty);
        }
        if (result == 0) {
            throw new IllegalArgumentException("Insufficient shares to sell");
        }
    }

    // -------------------------------------------------------------------------
    // Validation (DB read — fast fail before touching MQ)
    // -------------------------------------------------------------------------

    private void validateBuyOrder(OrderRequest req) {
        if (req.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (req.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        BigDecimal total = req.getPrice().multiply(BigDecimal.valueOf(req.getQuantity()));
        if (accountService.getAvailableBalance(req.getAccountId()).compareTo(total) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }
    }

    private void validateSellOrder(OrderRequest req) {
        if (req.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (req.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (portfolioService.getAvailableShares(req.getAccountId(), req.getStockSymbol())
                < req.getQuantity()) {
            throw new IllegalArgumentException("Insufficient shares to sell");
        }
    }

    // -------------------------------------------------------------------------
    // MQ
    // -------------------------------------------------------------------------

    private OrderMessage buildOrderMessage(OrderRequest req) {
        OrderMessage msg = new OrderMessage();
        msg.setOrderId(idGenerator.nextId());
        msg.setAccountId(req.getAccountId());
        msg.setStockSymbol(req.getStockSymbol());
        msg.setPrice(req.getPrice());
        msg.setTotalQuantity(req.getQuantity());
        msg.setRemainingQuantity(req.getQuantity());
        msg.setOrderType(req.getOrderType());
        msg.setOrderStatus(OrderStatus.OPEN);
        msg.setCreatedAt(java.time.LocalDateTime.now());
        return msg;
    }

    private void sendTransactionalOrder(OrderMessage orderMsg) {
        Message<OrderMessage> message = MessageBuilder
                .withPayload(orderMsg)
                .setHeader(RocketMQHeaders.KEYS, String.valueOf(orderMsg.getOrderId()))
                .setHeader(RocketMQHeaders.TAGS, orderMsg.getOrderType().name())
                .build();

        TransactionSendResult result =
                rocketMQTemplate.sendMessageInTransaction(ORDER_TOPIC, message, orderMsg);

        if (result.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
            throw new IllegalStateException("Order placement failed: insufficient funds or internal error");
        }
    }
}
