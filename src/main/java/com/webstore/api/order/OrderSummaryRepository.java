package com.webstore.api.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSummaryRepository extends JpaRepository<OrderSummary, Integer> {

    /**
     * The customer_id condition is pushed down into the view, so only this
     * customer's orders are aggregated.
     */
    Page<OrderSummary> findByCustomerId(Integer customerId, Pageable pageable);

    Page<OrderSummary> findByCustomerIdAndStatus(Integer customerId, String status, Pageable pageable);
}
