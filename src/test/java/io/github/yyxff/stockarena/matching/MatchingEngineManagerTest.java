package io.github.yyxff.stockarena.matching;

import io.github.yyxff.stockarena.matching.service.MatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MatchingEngineManagerTest {

    private MatchingEngineManager manager;
    private Map<Integer, MatchingEngine> engines;
    private static final int ENGINE_COUNT = 4;

    @BeforeEach
    void setUp() {
        manager = new MatchingEngineManager();

        // Bypass @PostConstruct — inject mock engines directly
        engines = new HashMap<>();
        for (int i = 0; i < ENGINE_COUNT; i++) {
            engines.put(i, mock(MatchingEngine.class));
        }
        ReflectionTestUtils.setField(manager, "engines", engines);
        ReflectionTestUtils.setField(manager, "engineCount", ENGINE_COUNT);
    }

    // -------------------------------------------------------------------------
    // Routing consistency
    // -------------------------------------------------------------------------

    @Test
    void sameSymbolAlwaysRoutesToSameEngine() {
        MatchingEngine first  = manager.getEngineBySymbol("AAPL");
        MatchingEngine second = manager.getEngineBySymbol("AAPL");
        MatchingEngine third  = manager.getEngineBySymbol("AAPL");

        assertThat(first).isSameAs(second).isSameAs(third);
    }

    @Test
    void routingIsStableAcrossMultipleSymbols() {
        String[] symbols = {"AAPL", "GOOG", "TSLA", "MSFT", "AMZN", "META", "NVDA", "BABA"};

        for (String symbol : symbols) {
            MatchingEngine first  = manager.getEngineBySymbol(symbol);
            MatchingEngine second = manager.getEngineBySymbol(symbol);
            assertThat(first)
                    .as("Symbol %s must always route to same engine", symbol)
                    .isSameAs(second);
        }
    }

    @Test
    void returnsValidEngineForAnySymbol() {
        String[] symbols = {"AAPL", "GOOG", "TSLA", "MSFT"};
        for (String symbol : symbols) {
            assertThat(manager.getEngineBySymbol(symbol)).isNotNull();
        }
    }

    @Test
    void routingDistributesAcrossAllEngines() {
        // With enough symbols, all 4 engines should be used at least once
        String[] symbols = IntStream.range(0, 100)
                .mapToObj(i -> "SYM" + i)
                .toArray(String[]::new);

        Set<MatchingEngine> usedEngines = java.util.Arrays.stream(symbols)
                .map(manager::getEngineBySymbol)
                .collect(Collectors.toSet());

        assertThat(usedEngines).hasSize(ENGINE_COUNT);
    }

    @Test
    void hashIsPositiveModuloSafe() {
        // Symbol with negative hashCode should not throw or return null
        // "polygenelubricants" is a well-known string with negative hashCode
        String negativeHashSymbol = "polygenelubricants";
        assertThat(negativeHashSymbol.hashCode()).isNegative();

        MatchingEngine engine = manager.getEngineBySymbol(negativeHashSymbol);
        assertThat(engine).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Initialization flag
    // -------------------------------------------------------------------------

    @Test
    void initializedIsFalseByDefault() {
        assertThat(manager.isInitialized()).isFalse();
    }

    @Test
    void markInitializedSetsFlag() {
        manager.markInitialized();
        assertThat(manager.isInitialized()).isTrue();
    }
}
