package io.github.yyxff.stockarena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockArenaApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockArenaApplication.class, args);
    }

}
