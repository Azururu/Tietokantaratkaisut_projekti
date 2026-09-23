package com.webstore.api.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webstore.api.common.NotFoundException;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderProcedures procedures;

    public OrderService(OrderRepository orders, OrderProcedures procedures) {
        this.orders = orders;
        this.procedures = procedures;
    }

    @Transactional(readOnly = true)
    public OrderResponse get(int id) {
        return orders.findWithDetailsById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    // No @Transactional here: the procedures manage their own transaction.
    public OrderResponse place(CreateOrderRequest request) {
        int id = procedures.placeOrder(request);
        return get(id);
    }

    public OrderResponse cancel(int id) {
        procedures.cancelOrder(id);
        return get(id);
    }
}
