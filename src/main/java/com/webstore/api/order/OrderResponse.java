package com.webstore.api.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetails(
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
    }

    public record Item(
            int productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }
}
