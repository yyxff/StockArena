package io.github.yyxff.stockarena.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KlineWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // key: symbol, value: list of sessions subscribed
    private final Map<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("WebSocket connection established: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 清理 session
        subscribers.values().forEach(sessions -> sessions.remove(session));
        System.out.println("WebSocket connection closed: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 前端发来的订阅请求
        Map<String, String> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String action = payload.get("action");
        String symbol = payload.get("symbol");

        if ("subscribe".equals(action) && symbol != null) {
            subscribers.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session);
        } else if ("unsubscribe".equals(action) && symbol != null) {
            subscribers.getOrDefault(symbol, Collections.emptySet()).remove(session);
        }
    }

    // 后端调用这个方法推送新 K 线给对应订阅者
    public void sendKline(String symbol, Object klineData) {
        Set<WebSocketSession> sessions = subscribers.getOrDefault(symbol, Collections.emptySet());
        sessions.forEach(session -> {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(klineData)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}