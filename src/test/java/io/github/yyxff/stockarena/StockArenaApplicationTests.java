package io.github.yyxff.stockarena;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Integration test — requires PostgreSQL, Redis and RocketMQ running")
@SpringBootTest
class StockArenaApplicationTests {

    @Test
    void contextLoads() {
    }

}
