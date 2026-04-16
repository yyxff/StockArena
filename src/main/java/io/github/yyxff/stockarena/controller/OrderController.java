package io.github.yyxff.stockarena.controller;

import io.github.yyxff.stockarena.dto.OrderRequest;
import io.github.yyxff.stockarena.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * Issue a one-time order token.
     * The frontend calls this when the user opens the order form.
     * The returned token must be included in the subsequent place-order request.
     */
    @GetMapping("/token")
    public String getOrderToken(@RequestParam Long accountId) {
        return orderService.generateOrderToken(accountId);
    }

    @PostMapping("/sell")
    public void placeSellOrder(@RequestBody OrderRequest orderRequest) {
        orderService.placeSellOrder(orderRequest);
    }

    @PostMapping("/buy")
    public void placeBuyOrder(@RequestBody OrderRequest orderRequest) {
        orderService.placeBuyOrder(orderRequest);
    }
}
