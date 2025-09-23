package io.github.yyxff.stockarena.kline;

import io.github.yyxff.stockarena.websocket.KlineWebSocketHandler;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class KLineSubscriber implements MessageListener {

    private final KlineWebSocketHandler webSocketHandler;

    public KLineSubscriber(KlineWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String klineData = new String(message.getBody());

            // 从频道名提取股票代码 "kline:AAPL" -> "AAPL"
            String stock = channel.substring(6);

            // 通过WebSocket推送给前端
            webSocketHandler.sendKline(stock, klineData);

            System.out.println("Received kline from Redis: " + channel + " -> " + klineData);
        } catch (Exception e) {
            System.err.println("Error processing kline message: " + e.getMessage());
        }
    }
}
