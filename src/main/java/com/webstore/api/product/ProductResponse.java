package com.webstore.api.product;

import java.math.BigDecimal;

public record ProductResponse(
        int id,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        boolean inStock,
        Integer categoryId,
        String categoryName,
        Integer supplierId,
        String supplierName,
        int version) {

    static ProductResponse from(CatalogProduct p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStockQuantity(),
                p.isInStock(),
                p.getCategoryId(),
                p.getCategoryName(),
                p.getSupplierId(),
                p.getSupplierName(),
                p.getVersion());
    }
}
