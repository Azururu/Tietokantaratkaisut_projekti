package com.webstore.api.order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.webstore.api.customer.Customer;
import com.webstore.api.customer.CustomerAddress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;

/**
 * New orders are created by the place_order procedure, not by persisting this entity,
 * so that stock checks and updates run in the same database transaction.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Set by the database default (current_timestamp()). */
    @Column(name = "order_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime orderDate;

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id")
    private CustomerAddress shippingAddress;

    /** Changes are logged to orderlog by the orderstatuschange trigger. */
    @Convert(converter = OrderStatusConverter.class)
    @Column(length = 50)
    private OrderStatus status;

    @OneToMany(mappedBy = "order")
    @OrderBy("id.productId")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Integer getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public LocalDateTime getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDateTime deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public CustomerAddress getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(CustomerAddress shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
