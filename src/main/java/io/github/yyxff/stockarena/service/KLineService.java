package io.github.yyxff.stockarena.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yyxff.stockarena.dto.KLineDTO;
import io.github.yyxff.stockarena.dto.KLineMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.websocket.KlineWebSocketHandler;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// TODO: Recover kline data from db to redis
@Service
public class KLineService {

    private static final String KLINE_TOPIC = "kline-topic";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, KLineDTO> currentKLines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> dirtyFlags = new ConcurrentHashMap<>();
    private final KlineWebSocketHandler klineWebSocketHandler;
    private final RocketMQTemplate rocketMQTemplate;

    @Autowired
    public KLineService(RedisTemplate<String, String> redisTemplate,
                        KlineWebSocketHandler klineWebSocketHandler,
                        RocketMQTemplate rocketMQTemplate) {
        this.redisTemplate = redisTemplate;
        this.klineWebSocketHandler = klineWebSocketHandler;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void updateCurrentKLine(TradeMessage trade) {
        String stock = trade.getStockSymbol();
        long tradeMinute = trade.getCreatedAt() / 60000 * 60000;

        currentKLines.compute(stock, (key, currentKLineDTO) -> {
            if (currentKLineDTO == null) {
                return createNewKLine(trade, tradeMinute);
            }

            if (tradeMinute > currentKLineDTO.getTimestamp()) {
                moveToCompletedQueue(stock, currentKLineDTO);
                return createNewKLine(trade, tradeMinute);
            } else {
                updateExistingKLine(currentKLineDTO, trade);
                return currentKLineDTO;
            }
        });

        dirtyFlags.put(stock, true);
    }

    private final ConcurrentHashMap<String, Queue<KLineDTO>> completedKLines = new ConcurrentHashMap<>();

    private void moveToCompletedQueue(String stock, KLineDTO completedKLineDTO) {
        completedKLines.computeIfAbsent(stock, k -> new LinkedList<>()).offer(completedKLineDTO);
    }

    @Scheduled(fixedRate = 1000)
    public void flushToRedis() throws JsonProcessingException {
        flushCompletedKLines();
        flushCurrentKLines();
    }

    private void flushCompletedKLines() throws JsonProcessingException {
        for (Map.Entry<String, Queue<KLineDTO>> entry : completedKLines.entrySet()) {
            String stock = entry.getKey();
            Queue<KLineDTO> queue = entry.getValue();

            while (!queue.isEmpty()) {
                KLineDTO completedKLineDTO = queue.poll();
                String json = objectMapper.writeValueAsString(completedKLineDTO);
                String redisKey = "kline:" + stock + ":1m";

                redisTemplate.opsForZSet().add(redisKey, json, completedKLineDTO.getTimestamp());
                sendCompletedKLineToMQ(stock, completedKLineDTO);
            }
        }
    }

    private void sendCompletedKLineToMQ(String stock, KLineDTO completedKLineDTO) {
        try {
            KLineMessage updateMessage = new KLineMessage(stock, completedKLineDTO, "1m");
            rocketMQTemplate.convertAndSend(KLINE_TOPIC, updateMessage);
        } catch (Exception e) {
            System.err.println("Error sending completed KLine to MQ for " + stock + ": " + e.getMessage());
        }
    }

    private void flushCurrentKLines() throws JsonProcessingException {
        for (Map.Entry<String, Boolean> dirtyEntry : dirtyFlags.entrySet()) {
            if (!dirtyEntry.getValue()) continue;

            String stock = dirtyEntry.getKey();
            KLineDTO kline = currentKLines.get(stock);
            if (kline == null) continue;

            String key = "kline:" + stock + ":1m";
            String json = objectMapper.writeValueAsString(kline);

            redisTemplate.opsForZSet().add(key, json, kline.getTimestamp());

            String channel = "kline:" + stock;
            redisTemplate.convertAndSend(channel, json);

            dirtyFlags.put(stock, false);
        }
    }

    public List<KLineDTO> getKLinesByTimeRange(String stock, String interval, long startTs, long endTs) throws JsonProcessingException {
        String key = "kline:" + stock + ":" + interval;
        Set<String> members = redisTemplate.opsForZSet().rangeByScore(key, startTs, endTs);

        if (members == null || members.isEmpty()) return Collections.emptyList();

        List<KLineDTO> kLineDTOS = new ArrayList<>();
        for (String m : members) {
            kLineDTOS.add(objectMapper.readValue(m, KLineDTO.class));
        }
        return kLineDTOS;
    }

    private KLineDTO createNewKLine(TradeMessage trade, long timestamp) {
        KLineDTO kline = new KLineDTO();
        kline.setOpen(trade.getPrice());
        kline.setHigh(trade.getPrice());
        kline.setLow(trade.getPrice());
        kline.setClose(trade.getPrice());
        kline.setVolume(trade.getQuantity());
        kline.setTimestamp(timestamp);
        return kline;
    }

    private void updateExistingKLine(KLineDTO kline, TradeMessage trade) {
        kline.setHigh(kline.getHigh().max(trade.getPrice()));
        kline.setLow(kline.getLow().min(trade.getPrice()));
        kline.setClose(trade.getPrice());
        kline.setVolume(kline.getVolume() + trade.getQuantity());
    }
}
