package io.github.yyxff.stockarena.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yyxff.stockarena.config.KafkaTopics;
import io.github.yyxff.stockarena.dto.KLineDTO;
import io.github.yyxff.stockarena.dto.KLineMessage;
import io.github.yyxff.stockarena.dto.TradeMessage;
import io.github.yyxff.stockarena.websocket.KlineWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// TODO: Recover kline data from db to redis
@Service
public class KLineService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, KLineDTO> currentKLines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> dirtyFlags = new ConcurrentHashMap<>(); // 脏数据标记
    private final KlineWebSocketHandler klineWebSocketHandler;
    private final KafkaTemplate<String, Object> kafkaTemplate; // 添加Kafka模板


    @Autowired
    public KLineService(RedisTemplate<String, String> redisTemplate, KlineWebSocketHandler klineWebSocketHandler, KafkaTemplate<String, Object> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.klineWebSocketHandler = klineWebSocketHandler;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void updateCurrentKLine(TradeMessage trade) {
        String stock = trade.getStockSymbol();
        long tradeMinute = trade.getCreatedAt() / 60000 * 60000; // 标准化到分钟
        
        currentKLines.compute(stock, (key, currentKLineDTO) -> {
            if (currentKLineDTO == null) {
                // Create new KLine if none exists
                return createNewKLine(trade, tradeMinute);
            }
            
            if (tradeMinute > currentKLineDTO.getTimestamp()) {
                // 新的分钟周期，先移除旧K线到完成队列，再创建新K线
                moveToCompletedQueue(stock, currentKLineDTO);
                return createNewKLine(trade, tradeMinute);
            } else {
                // 同一分钟内，更新现有K线
                updateExistingKLine(currentKLineDTO, trade);
                return currentKLineDTO;
            }
        });
        
        // 标记为脏数据
        dirtyFlags.put(stock, true);
    }
    
    // 完成的K线队列，等待批量保存
    private final ConcurrentHashMap<String, Queue<KLineDTO>> completedKLines = new ConcurrentHashMap<>();

    /**
     * 将完成的K线移动到待保存队列
     */
    private void moveToCompletedQueue(String stock, KLineDTO completedKLineDTO) {
        completedKLines.computeIfAbsent(stock, k -> new LinkedList<>()).offer(completedKLineDTO);
        System.out.println("Moved completed KLine to queue for " + stock +
                          " at " + new Date(completedKLineDTO.getTimestamp()));
    }

    @Scheduled(fixedRate = 1000) // flush by every second
    public void flushToRedis() throws JsonProcessingException {
        // 1. 先处理完成的K线（历史数据，保存到Redis + 发送到MQ落库）
        flushCompletedKLines();

        // 2. 再处理当前的脏K线（实时数据，保存并推送）
        flushCurrentKLines();
    }
    
    /**
     * 刷新完成的K线到Redis（保存到Redis + 发送到MQ）
     */
    private void flushCompletedKLines() throws JsonProcessingException {
        for (Map.Entry<String, Queue<KLineDTO>> entry : completedKLines.entrySet()) {
            String stock = entry.getKey();
            Queue<KLineDTO> queue = entry.getValue();

            while (!queue.isEmpty()) {
                KLineDTO completedKLineDTO = queue.poll();
                String json = objectMapper.writeValueAsString(completedKLineDTO);
                String redisKey = "kline:" + stock + ":1m";

                // 1. 保存到Redis
                redisTemplate.opsForZSet().add(redisKey, json, completedKLineDTO.getTimestamp());

                // 2. 发送到Kafka进行落库 - 这是新增的功能
                sendCompletedKLineToMQ(stock, completedKLineDTO);

                System.out.println("Saved and sent completed KLine for " + stock + ": " + json);
            }
        }
    }

    /**
     * 发送完成的K线到MQ进行落库
     */
    private void sendCompletedKLineToMQ(String stock, KLineDTO completedKLineDTO) {
        try {
            // 创建K线更新消息，包含股票代码和K线数据
            KLineMessage updateMessage = new KLineMessage(stock, completedKLineDTO, "1m");

            // 使用stock作为key确保同一股票的K线数据有序处理
            kafkaTemplate.send(KafkaTopics.KLINE_UPDATES, stock, updateMessage)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        System.out.println("Successfully sent completed KLine to MQ for " + stock +
                                         " at " + new Date(completedKLineDTO.getTimestamp()));
                    } else {
                        System.err.println("Failed to send KLine to MQ for " + stock + ": " + ex.getMessage());
                    }
                });
        } catch (Exception e) {
            System.err.println("Error sending completed KLine to MQ for " + stock + ": " + e.getMessage());
        }
    }

    /**
     * 刷新当前K线到Redis（保存并推送实时数据）
     */
    private void flushCurrentKLines() throws JsonProcessingException {
        for (Map.Entry<String, Boolean> dirtyEntry : dirtyFlags.entrySet()) {
            if (!dirtyEntry.getValue()) continue; // 跳过非脏数据

            String stock = dirtyEntry.getKey();
            KLineDTO kline = currentKLines.get(stock);
            if (kline == null) continue;

            String key = "kline:" + stock + ":1m";
            String json = objectMapper.writeValueAsString(kline);

            // 存储到Redis ZSet（当前分钟的实时K线）
            redisTemplate.opsForZSet().add(key, json, kline.getTimestamp());

            // 发布到Redis Pub/Sub频道（只推送实时变化的数据）
            String channel = "kline:" + stock;
            redisTemplate.convertAndSend(channel, json);

            // 清除脏标记
            dirtyFlags.put(stock, false);

            System.out.println("Flushed current KLine data for " + stock + ": " + json);
        }
    }

    public List<KLineDTO> getKLinesByTimeRange(String stock, String interval, long startTs, long endTs) throws JsonProcessingException {
        String key = "kline:" + stock + ":" + interval;
        // Redis ZSet search by score(timestamp) range
        Set<String> members = redisTemplate.opsForZSet().rangeByScore(key, startTs, endTs);

        if (members == null || members.isEmpty()) return Collections.emptyList();

        List<KLineDTO> kLineDTOS = new ArrayList<>();
        for (String m : members) {
            kLineDTOS.add(objectMapper.readValue(m, KLineDTO.class));
        }
        return kLineDTOS;
    }

    // @Scheduled(fixedRate = 3000)
    // public void getKLinesExample() throws JsonProcessingException {
    //     long now = System.currentTimeMillis();
    //     long oneHourAgo = now - 3600 * 1000;
    //     List<KLine> kLines = getKLinesByTimeRange("AAPL", "1m", oneHourAgo, now);
    //     System.out.println("Retrieved KLines from Redis: " + kLines);
    // }

    /**
     * 创建新的K线
     */
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

    /**
     * 更新现有K线数据
     */
    private void updateExistingKLine(KLineDTO kline, TradeMessage trade) {
        kline.setHigh(kline.getHigh().max(trade.getPrice()));
        kline.setLow(kline.getLow().min(trade.getPrice()));
        kline.setClose(trade.getPrice());
        kline.setVolume(kline.getVolume() + trade.getQuantity());
    }
}
