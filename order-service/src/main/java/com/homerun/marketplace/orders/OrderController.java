package com.homerun.marketplace.orders;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.homerun.marketplace.orders.OrderModels.*;

@RestController
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Receipt place(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateOrder request) {
        return orders.place(key, request);
    }

    @GetMapping("/api/admin/orders")
    public List<AdminOrder> all() { return orders.all(); }

    @GetMapping("/api/admin/orders/{id}")
    public AdminOrder one(@PathVariable String id) { return orders.one(id); }

    @PatchMapping("/api/admin/orders/{id}/status")
    public AdminOrder status(@PathVariable String id, @Valid @RequestBody StatusUpdate input) {
        return orders.changeStatus(id, input.status());
    }
}
