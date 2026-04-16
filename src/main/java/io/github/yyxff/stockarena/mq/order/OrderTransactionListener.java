package io.github.yyxff.stockarena.mq.order;

import io.github.yyxff.stockarena.dto.OrderMessage;
import io.github.yyxff.stockarena.repository.OrderRepository;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RocketMQTransactionListener
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private OrderLocalTransactionService orderLocalTransactionService;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Called synchronously after the half-message is sent successfully.
     * Freeze funds + save order in one DB transaction.
     * Return COMMIT on success, ROLLBACK on any failure.
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            OrderMessage orderMsg = (OrderMessage) arg;
            orderLocalTransactionService.freezeAndSaveOrder(orderMsg);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * Called by RocketMQ broker when the transaction state is unknown
     * (e.g. after a crash before commit/rollback was sent).
     * Check the DB: if the order exists the local transaction committed, otherwise roll back.
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String keys = (String) msg.getHeaders().get(RocketMQHeaders.KEYS);
        Long orderId = Long.parseLong(keys);
        return orderRepository.existsById(orderId)
                ? RocketMQLocalTransactionState.COMMIT
                : RocketMQLocalTransactionState.ROLLBACK;
    }
}
