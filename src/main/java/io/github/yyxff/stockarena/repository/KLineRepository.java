package io.github.yyxff.stockarena.repository;

import io.github.yyxff.stockarena.model.KLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KLineRepository extends JpaRepository<KLine, Long> {

    /**
     * 根据股票代码和时间戳查找K线数据
     * 用于幂等性检查
     */
    Optional<KLine> findBySymbolAndTimestamp(String symbol, Long timestamp);

    /**
     * 检查是否存在指定的K线数据
     * 用于快速幂等性检查
     */
    @Query("SELECT COUNT(k) > 0 FROM KLine k WHERE k.symbol = :symbol AND k.timestamp = :timestamp")
    boolean existsBySymbolAndTimestamp(@Param("symbol") String symbol, @Param("timestamp") Long timestamp);
}
