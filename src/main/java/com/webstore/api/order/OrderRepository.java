package com.webstore.api.order;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    /** Loads the order with customer, address, items and products in one query. */
    @EntityGraph(attributePaths = { "customer", "shippingAddress", "items", "items.product" })
    Optional<Order> findWithDetailsById(Integer id);

    /** SELECT … FOR UPDATE: other transactions wait until this one ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findForUpdateById(Integer id);

    boolean existsByCustomerId(Integer customerId);

    boolean existsByShippingAddressId(Integer addressId);
}
