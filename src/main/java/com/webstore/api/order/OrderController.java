package com.webstore.api.order;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webstore.api.common.NotFoundException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orders;

    public OrderController(OrderRepository orders) {
        this.orders = orders;
    }

    @GetMapping("/{id}")
    public OrderDetails get(@PathVariable int id) {
        return orders.findById(id)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    @PostMapping
    public ResponseEntity<OrderDetails> create(@Valid @RequestBody CreateOrderRequest request) {
        int id = orders.placeOrder(request);
        return ResponseEntity.created(URI.create("/api/orders/" + id)).body(get(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderDetails cancel(@PathVariable int id) {
        orders.cancelOrder(id);
        return get(id);
    }
}
