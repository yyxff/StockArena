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

    // -------------------------------------------------------------------------
    // Token
    // -------------------------------------------------------------------------

    /**
     * Generate a one-time order token for the given account and store it in Redis
     * with a 5-minute TTL. The token is a snowflake ID (string form).
     */
    public String generateOrderToken(Long accountId) {
        String token = String.valueOf(idGenerator.nextId());
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, String.valueOf(accountId), TOKEN_TTL);
        return token;
    }

    /**
     * Validate and consume the token atomically using GETDEL.
     * Throws if the token is missing, expired, already used, or bound to a
     * different account.
     */
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

    public void placeBuyOrder(OrderRequest orderRequest) {
        consumeToken(orderRequest.getToken(), orderRequest.getAccountId());
        validateBuyOrder(orderRequest);
        OrderMessage orderMsg = buildOrderMessage(orderRequest);
        sendTransactionalOrder(orderMsg);
    }

    public void placeSellOrder(OrderRequest orderRequest) {
        consumeToken(orderRequest.getToken(), orderRequest.getAccountId());
        validateSellOrder(orderRequest);
        OrderMessage orderMsg = buildOrderMessage(orderRequest);
        sendTransactionalOrder(orderMsg);
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validateBuyOrder(OrderRequest orderRequest) {
        if (orderRequest.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (orderRequest.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        BigDecimal totalPrice = orderRequest.getPrice()
                .multiply(BigDecimal.valueOf(orderRequest.getQuantity()));
        if (accountService.getAvailableBalance(orderRequest.getAccountId()).compareTo(totalPrice) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }
    }

    private void validateSellOrder(OrderRequest orderRequest) {
        if (orderRequest.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (orderRequest.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (portfolioService.getAvailableShares(orderRequest.getAccountId(), orderRequest.getStockSymbol())
                < orderRequest.getQuantity()) {
            throw new IllegalArgumentException("Insufficient shares to sell");
        }
    }

    // -------------------------------------------------------------------------
    // MQ
    // -------------------------------------------------------------------------

    private OrderMessage buildOrderMessage(OrderRequest orderRequest) {
        OrderMessage orderMsg = new OrderMessage();
        orderMsg.setOrderId(idGenerator.nextId());   // fresh snowflake, independent of token
        orderMsg.setAccountId(orderRequest.getAccountId());
        orderMsg.setStockSymbol(orderRequest.getStockSymbol());
        orderMsg.setPrice(orderRequest.getPrice());
        orderMsg.setTotalQuantity(orderRequest.getQuantity());
        orderMsg.setRemainingQuantity(orderRequest.getQuantity());
        orderMsg.setOrderType(orderRequest.getOrderType());
        orderMsg.setOrderStatus(OrderStatus.OPEN);
        orderMsg.setCreatedAt(java.time.LocalDateTime.now());
        return orderMsg;
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
