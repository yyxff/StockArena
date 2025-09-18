package io.github.yyxff.stockarena.controller;

import io.github.yyxff.stockarena.dto.OrderRequest;
import io.github.yyxff.stockarena.service.OrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/sell")
    public void placeSellOrder(@RequestBody OrderRequest orderRequest) {

    }

    @PostMapping("/buy")
    public void placeBuyOrder(@RequestBody OrderRequest orderRequest) {

    }


}
