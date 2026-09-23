package com.webstore.api.order;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    /** Loads the order with customer, address, items and products in one query. */
    @EntityGraph(attributePaths = { "customer", "shippingAddress", "items", "items.product" })
    Optional<Order> findWithDetailsById(Integer id);
}
