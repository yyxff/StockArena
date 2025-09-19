package io.github.yyxff.stockarena.repository;

import io.github.yyxff.stockarena.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o " +
            "WHERE o.orderType = 'SELL' AND o.stockSymbol = :stockSymbol AND o.price <= :price " +
            "ORDER BY o.price ASC, o.createdAt ASC " +
            "LIMIT 1")
    Optional<Order> findBestSellOrder(@Param("stockSymbol") String stockSymbol, @Param("price") BigDecimal price);

    @Query("SELECT o FROM Order o " +
            "WHERE o.orderType = 'BUY' AND o.stockSymbol = :stockSymbol AND o.price >= :price " +
            "ORDER BY o.price DESC , o.createdAt ASC " +
            "LIMIT 1")
    Optional<Order> findBestBuyOrder(@Param("stockSymbol") String stockSymbol, @Param("price") BigDecimal price);
}
