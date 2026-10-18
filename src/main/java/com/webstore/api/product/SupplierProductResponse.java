package com.webstore.api.product;

import java.math.BigDecimal;

/**
 * A product in GET /api/suppliers/{id}/products. primarySupplier: whether the
 * supplier is also the product's primary supplier (products.supplier_id), or only
 * linked through the product_suppliers join table.
 */
public record SupplierProductResponse(
        int id,
        String name,
        BigDecimal price,
        int stockQuantity,
        boolean primarySupplier) {

    static SupplierProductResponse from(Product p, int supplierId) {
        // getSupplier() is a lazy proxy; reading its id doesn't run a query.
        boolean primary = p.getSupplier() != null && p.getSupplier().getId() == supplierId;
        return new SupplierProductResponse(p.getId(), p.getName(), p.getPrice(), p.getStockQuantity(), primary);
    }
}
