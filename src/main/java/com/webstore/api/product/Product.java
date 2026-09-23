package com.webstore.api.product;

import java.math.BigDecimal;

public record Product(
        int id,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        Integer categoryId,
        String categoryName,
        Integer supplierId,
        String supplierName) {
}
