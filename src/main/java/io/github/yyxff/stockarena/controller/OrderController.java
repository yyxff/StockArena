package io.github.yyxff.stockarena.controller;

import io.github.yyxff.stockarena.dto.OrderRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/orders")
public class OrderController {

    @PostMapping("/place")
    public String placeOrder(@RequestBody OrderRequest orderRequest) {
        
    }

}
