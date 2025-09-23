package io.github.yyxff.stockarena.common;

import cn.hutool.core.lang.Snowflake;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {
    private final Snowflake snowflake;

    public IdGenerator() {
        long datacenterId = 1L; // Data center ID
        long machineId = 1L;    // Unique machine ID
        this.snowflake = new Snowflake(datacenterId, machineId);
    }

    public long nextId() {
        return snowflake.nextId();
    }
}