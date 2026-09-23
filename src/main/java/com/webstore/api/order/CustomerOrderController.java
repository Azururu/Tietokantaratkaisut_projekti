package com.webstore.api.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.webstore.api.common.NotFoundException;
import com.webstore.api.common.PageResponse;
import com.webstore.api.customer.CustomerRepository;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** A customer's order history, read from the v_order_summary view. */
@RestController
public class CustomerOrderController {

    private final OrderSummaryRepository summaries;
    private final CustomerRepository customers;

    public CustomerOrderController(OrderSummaryRepository summaries, CustomerRepository customers) {
        this.summaries = summaries;
        this.customers = customers;
    }

    @GetMapping("/api/customers/{customerId}/orders")
    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(
            @PathVariable int customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (!customers.existsById(customerId)) {
            throw new NotFoundException("Customer " + customerId + " not found");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));
        Page<OrderSummary> result = status == null
                ? summaries.findByCustomerId(customerId, pageable)
                : summaries.findByCustomerIdAndStatus(customerId, status.toUpperCase(), pageable);
        return new PageResponse<>(result.getContent(), page, size, result.getTotalElements());
    }
}
