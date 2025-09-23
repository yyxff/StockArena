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
    private final ConcurrentHashMap<String, Boolean> dirtyFlags = new ConcurrentHashMap<>(); // 脏数据标记
    private final KlineWebSocketHandler klineWebSocketHandler;


    @Autowired
    public KLineService(RedisTemplate<String, String> redisTemplate, KlineWebSocketHandler klineWebSocketHandler) {
        this.redisTemplate = redisTemplate;
        this.klineWebSocketHandler = klineWebSocketHandler;
    }

    public void updateCurrentKLine(TradeMessage trade) {
        String stock = trade.getStockSymbol();
        long tradeMinute = trade.getCreatedAt() / 60000 * 60000; // 标准化到分钟
        
        currentKLines.compute(stock, (key, currentKLine) -> {
            if (currentKLine == null) {
                // Create new KLine if none exists
                return createNewKLine(trade, tradeMinute);
            }
            
            if (tradeMinute > currentKLine.getTimestamp()) {
                // 新的分钟周期，保存旧K线并创建新K线
                saveCompletedKLine(stock, currentKLine);
                return createNewKLine(trade, tradeMinute);
            } else {
                // 同一分钟内，更新现有K线
                updateExistingKLine(currentKLine, trade);
                return currentKLine;
            }
        });
        
        // 标记为脏数据
        dirtyFlags.put(stock, true);
    }
    
    /**
     * 创建新的K线
     */
    private KLine createNewKLine(TradeMessage trade, long timestamp) {
        KLine kline = new KLine();
        kline.setOpen(trade.getPrice());
        kline.setHigh(trade.getPrice());
        kline.setLow(trade.getPrice());
        kline.setClose(trade.getPrice());
        kline.setVolume(trade.getQuantity());
        kline.setTimestamp(timestamp);
        return kline;
    }
    
    /**
     * 更新现有K线数据
     */
    private void updateExistingKLine(KLine kline, TradeMessage trade) {
        kline.setHigh(kline.getHigh().max(trade.getPrice()));
        kline.setLow(kline.getLow().min(trade.getPrice()));
        kline.setClose(trade.getPrice());
        kline.setVolume(kline.getVolume() + trade.getQuantity());
    }
    
    /**
     * 保存完成的K线到Redis
     */
    private void saveCompletedKLine(String stock, KLine completedKLine) {
        try {
            String json = objectMapper.writeValueAsString(completedKLine);
            String redisKey = "kline:" + stock + ":1m";
            redisTemplate.opsForZSet().add(redisKey, json, completedKLine.getTimestamp());
            System.out.println("Saved completed KLine for " + stock + ": " + json);
        } catch (JsonProcessingException e) {
            System.err.println("Error saving completed KLine for " + stock + ": " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 1000) // flush by every second
    public void flushToRedis() throws JsonProcessingException {
        // 只处理脏数据
        for (Map.Entry<String, Boolean> dirtyEntry : dirtyFlags.entrySet()) {
            if (!dirtyEntry.getValue()) continue; // 跳过非脏数据

            String stock = dirtyEntry.getKey();
            KLine kline = currentKLines.get(stock);
            if (kline == null) continue;

            String key = "kline:" + stock + ":1m";
            String json = objectMapper.writeValueAsString(kline);

            // 存储到Redis ZSet（当前分钟的实时K线）
            redisTemplate.opsForZSet().add(key, json, kline.getTimestamp());

            // 发布到Redis Pub/Sub频道（只推送有变化的数据）
            String channel = "kline:" + stock;
            redisTemplate.convertAndSend(channel, json);

            // 清除脏标记
            dirtyFlags.put(stock, false);

            System.out.println("Flushed dirty KLine data for " + stock + ": " + json);
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