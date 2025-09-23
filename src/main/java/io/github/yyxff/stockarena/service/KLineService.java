package io.github.yyxff.stockarena.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yyxff.stockarena.dto.KLine;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.websocket.KlineWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// TODO: Recover kline data from db to redis
@Service
public class KLineService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, KLine> currentKLines = new ConcurrentHashMap<>();
    private final KlineWebSocketHandler klineWebSocketHandler;


    @Autowired
    public KLineService(RedisTemplate<String, String> redisTemplate, KlineWebSocketHandler klineWebSocketHandler) {
        this.redisTemplate = redisTemplate;
        this.klineWebSocketHandler = klineWebSocketHandler;
    }

    public void updateCurrentKLine(TradeMessage trade) {
        String stock = trade.getStockSymbol();
        currentKLines.compute(stock, (key, kline) -> {
            if (kline == null) {
                kline = new KLine();
                kline.setOpen(trade.getPrice());
                kline.setHigh(trade.getPrice());
                kline.setLow(trade.getPrice());
                kline.setClose(trade.getPrice());
                kline.setVolume(trade.getQuantity());
                kline.setTimestamp(trade.getCreatedAt() / 60000 * 60000); // assign to minute start
            } else {
                kline.setHigh(kline.getHigh().max(trade.getPrice()));
                kline.setLow(kline.getLow().min(trade.getPrice()));
                kline.setClose(trade.getPrice());
                kline.setVolume(kline.getVolume() + trade.getQuantity());
            }
            return kline;
        });
    }

    @Scheduled(fixedRate = 1000) // flush by every second
    public void flushToRedis() throws JsonProcessingException {
        for (Map.Entry<String, KLine> entry : currentKLines.entrySet()) {
            KLine kline = entry.getValue();
            String key = "kline:" + entry.getKey() + ":1m";
            String json = objectMapper.writeValueAsString(kline);
            redisTemplate.opsForZSet().add(key, json, kline.getTimestamp());
            // klineWebSocketHandler.sendKline(entry.getKey(), json);

            // Publish Redis Pub/Sub Channel
            String channel = "kline:" + entry.getKey();
            redisTemplate.convertAndSend(channel, json);

            System.out.println("Flushed KLine to Redis: " + json);
        }
    }

    public List<KLine> getKLinesByTimeRange(String stock, String interval, long startTs, long endTs) throws JsonProcessingException {
        String key = "kline:" + stock + ":" + interval;
        // Redis ZSet search by score(timestamp) range
        Set<String> members = redisTemplate.opsForZSet().rangeByScore(key, startTs, endTs);

        if (members == null || members.isEmpty()) return Collections.emptyList();

        List<KLine> kLines = new ArrayList<>();
        for (String m : members) {
            kLines.add(objectMapper.readValue(m, KLine.class));
        }
        return kLines;
    }

    // @Scheduled(fixedRate = 3000)
    // public void getKLinesExample() throws JsonProcessingException {
    //     long now = System.currentTimeMillis();
    //     long oneHourAgo = now - 3600 * 1000;
    //     List<KLine> kLines = getKLinesByTimeRange("AAPL", "1m", oneHourAgo, now);
    //     System.out.println("Retrieved KLines from Redis: " + kLines);
    // }
}