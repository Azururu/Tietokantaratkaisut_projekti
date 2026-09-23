package com.webstore.api.product;

import java.math.BigDecimal;

public record ProductResponse(
        int id,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        Integer categoryId,
        String categoryName,
        Integer supplierId,
        String supplierName) {

    static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStockQuantity(),
                p.getCategory() == null ? null : p.getCategory().getId(),
                p.getCategory() == null ? null : p.getCategory().getName(),
                p.getSupplier() == null ? null : p.getSupplier().getId(),
                p.getSupplier() == null ? null : p.getSupplier().getName());
    }
}
