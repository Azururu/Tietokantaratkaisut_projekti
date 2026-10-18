package com.webstore.api.order;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webstore.api.common.ConflictException;
import com.webstore.api.common.NotFoundException;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderProcedures procedures;
    private final OrderLogRepository log;

    public OrderService(OrderRepository orders, OrderProcedures procedures, OrderLogRepository log) {
        this.orders = orders;
        this.procedures = procedures;
        this.log = log;
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

    /**
     * Marks a NEW order as shipped. The row is locked (SELECT … FOR UPDATE) for the rest
     * of the transaction, so a simultaneous cancel or ship waits and then sees the new
     * status. The orderstatuschange trigger logs the change.
     */
    @Transactional
    public OrderResponse ship(int id) {
        Order order = orders.findForUpdateById(id)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
        if (order.getStatus() != OrderStatus.NEW) {
            throw new ConflictException("Only NEW orders can be shipped");
        }
        order.setStatus(OrderStatus.SHIPPED);
        order.setDeliveryDate(LocalDateTime.now().withNano(0)); // DATETIME column: whole seconds
        orders.flush();
        return get(id);
    }

    /** Status changes recorded by the orderstatuschange trigger, oldest first. */
    @Transactional(readOnly = true)
    public List<OrderLog> statusHistory(int id) {
        if (!orders.existsById(id)) {
            throw new NotFoundException("Order " + id + " not found");
        }
        return log.findByOrderIdOrderByLogId(id);
    }
}
