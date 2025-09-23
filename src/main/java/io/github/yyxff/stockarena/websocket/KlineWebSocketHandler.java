package io.github.yyxff.stockarena.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KlineWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // key: symbol, value: set of sessions subscribed to this symbol
    private final Map<String, Set<WebSocketSession>> symbolSubscribers = new ConcurrentHashMap<>();

    // key: sessionId, value: set of symbols this session subscribed to
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
        System.out.println("WebSocket connection established: " + session.getId());

        // 发送连接确认消息
        Map<String, Object> response = new HashMap<>();
        response.put("type", "connection");
        response.put("status", "connected");
        response.put("message", "WebSocket connected successfully");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        Set<String> subscribedSymbols = sessionSubscriptions.remove(sessionId);

        if (subscribedSymbols != null) {
            // 从所有订阅的股票中移除该会话
            for (String symbol : subscribedSymbols) {
                Set<WebSocketSession> sessions = symbolSubscribers.get(symbol);
                if (sessions != null) {
                    sessions.remove(session);
                    if (sessions.isEmpty()) {
                        symbolSubscribers.remove(symbol);
                    }
                }
            }
        }

        System.out.println("WebSocket connection closed: " + sessionId +
            ", was subscribed to: " + subscribedSymbols);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(message.getPayload());
            String action = jsonNode.get("action").asText();

            switch (action) {
                case "subscribe":
                    handleSubscribe(session, jsonNode);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session, jsonNode);
                    break;
                case "ping":
                    handlePing(session);
                    break;
                default:
                    sendErrorResponse(session, "Unknown action: " + action);
            }
        } catch (Exception e) {
            System.err.println("Error handling WebSocket message: " + e.getMessage());
            sendErrorResponse(session, "Invalid message format");
        }
    }

    private void handleSubscribe(WebSocketSession session, JsonNode jsonNode) throws Exception {
        String symbol = jsonNode.get("symbol").asText();
        String sessionId = session.getId();

        // 添加到订阅映射
        symbolSubscribers.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionSubscriptions.get(sessionId).add(symbol);

        // 发送订阅确认
        Map<String, Object> response = new HashMap<>();
        response.put("type", "subscribe");
        response.put("symbol", symbol);
        response.put("status", "success");
        response.put("message", "Subscribed to " + symbol + " kline data");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

        System.out.println("Session " + sessionId + " subscribed to " + symbol);
    }

    private void handleUnsubscribe(WebSocketSession session, JsonNode jsonNode) throws Exception {
        String symbol = jsonNode.get("symbol").asText();
        String sessionId = session.getId();

        // 从订阅映射中移除
        Set<WebSocketSession> sessions = symbolSubscribers.get(symbol);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                symbolSubscribers.remove(symbol);
            }
        }

        Set<String> userSymbols = sessionSubscriptions.get(sessionId);
        if (userSymbols != null) {
            userSymbols.remove(symbol);
        }

        // 发送取消订阅确认
        Map<String, Object> response = new HashMap<>();
        response.put("type", "unsubscribe");
        response.put("symbol", symbol);
        response.put("status", "success");
        response.put("message", "Unsubscribed from " + symbol + " kline data");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

        System.out.println("Session " + sessionId + " unsubscribed from " + symbol);
    }

    private void handlePing(WebSocketSession session) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "pong");
        response.put("timestamp", System.currentTimeMillis());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void sendErrorResponse(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "error");
            response.put("message", errorMessage);
            response.put("timestamp", System.currentTimeMillis());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            System.err.println("Failed to send error response: " + e.getMessage());
        }
    }

    // 后端调用这个方法推送K线数据给订阅者
    public void sendKline(String symbol, String klineData) {
        Set<WebSocketSession> sessions = symbolSubscribers.getOrDefault(symbol, Collections.emptySet());

        if (sessions.isEmpty()) {
            return; // 没有订阅者，直接返回
        }

        // 构造推送消息
        Map<String, Object> message = new HashMap<>();
        message.put("type", "kline");
        message.put("symbol", symbol);
        message.put("data", klineData);
        message.put("timestamp", System.currentTimeMillis());

        try {
            String messageStr = objectMapper.writeValueAsString(message);

            sessions.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(messageStr));
                        return false;
                    }
                } catch (Exception e) {
                    System.err.println("Error sending kline data to session: " + e.getMessage());
                }
                return true; // 移除失效的连接
            });

            System.out.println("Sent kline data to " + sessions.size() + " subscribers for symbol: " + symbol);
        } catch (Exception e) {
            System.err.println("Error creating kline message: " + e.getMessage());
        }
    }

    // 获取连接统计信息
    public Map<String, Object> getConnectionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", sessionSubscriptions.size());
        stats.put("totalSymbolSubscriptions", symbolSubscribers.size());

        Map<String, Integer> symbolStats = new HashMap<>();
        symbolSubscribers.forEach((symbol, sessions) ->
            symbolStats.put(symbol, sessions.size()));
        stats.put("subscribersBySymbol", symbolStats);

        return stats;
    }
}