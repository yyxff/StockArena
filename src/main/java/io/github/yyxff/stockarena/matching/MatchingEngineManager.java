package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class MatchingEngineManager {

    private final Map<Integer, MatchingEngine> engines = new HashMap<>();
    private final int partitionCount = 4;

    // 通过构造器注入 partitionCount，可从 application.yml 配置读取
    public MatchingEngineManager() {
        // this.partitionCount = partitionCount;
        initEngines();
    }

    private void initEngines() {
        for (int i = 0; i < partitionCount; i++) {
            engines.put(i, new MatchingEngine("Engine-" + i));
        }
        System.out.println("初始化 " + partitionCount + " 个 MatchingEngine");
    }

    public MatchingEngine getEngineByPartition(int partition) {
        return engines.get(partition);
    }

    public Collection<MatchingEngine> getAllEngines() {
        return engines.values();
    }
}