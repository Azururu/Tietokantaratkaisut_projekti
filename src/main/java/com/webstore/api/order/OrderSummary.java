package com.webstore.api.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only mapping of the v_order_summary view (sql/06_views.sql): one row per order
 * with customer name, item counts and order total.
 */
@Entity
@Immutable
@Table(name = "v_order_summary")
public class OrderSummary {

    @Id
    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "order_date")
    private LocalDateTime orderDate;

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    private String status;

    @Column(name = "item_count")
    private long itemCount;

    @Column(name = "total_quantity")
    private long totalQuantity;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    protected OrderSummary() {
    }

    public Integer getOrderId() {
        return orderId;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public LocalDateTime getDeliveryDate() {
        return deliveryDate;
    }

    public String getStatus() {
        return status;
    }

    public long getItemCount() {
        return itemCount;
    }

    public long getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
