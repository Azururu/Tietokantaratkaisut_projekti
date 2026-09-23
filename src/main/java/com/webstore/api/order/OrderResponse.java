package com.webstore.api.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.webstore.api.customer.CustomerAddress;

public record OrderResponse(
        int id,
        int customerId,
        String customerName,
        LocalDateTime orderDate,
        LocalDateTime deliveryDate,
        String status,
        ShippingAddress shippingAddress,
        List<Item> items,
        BigDecimal total) {

    public record ShippingAddress(
            int id,
            String streetAddress,
            String postalCode,
            String city,
            String country) {

        static ShippingAddress from(CustomerAddress a) {
            return a == null ? null
                    : new ShippingAddress(a.getId(), a.getStreetAddress(), a.getPostalCode(),
                            a.getCity(), a.getCountry());
        }
    }

    public record Item(
            int productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {

        static Item from(OrderItem item) {
            return new Item(item.getProduct().getId(), item.getProduct().getName(),
                    item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
        }
    }

    static OrderResponse from(Order order) {
        List<Item> items = order.getItems().stream().map(Item::from).toList();
        BigDecimal total = items.stream()
                .map(Item::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getCustomer().getFullName(),
                order.getOrderDate(),
                order.getDeliveryDate(),
                order.getStatus(),
                ShippingAddress.from(order.getShippingAddress()),
                items,
                total);
    }
}
