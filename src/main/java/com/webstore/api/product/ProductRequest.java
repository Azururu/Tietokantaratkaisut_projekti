package com.webstore.api.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST and PUT /api/products. The limits match the products table (price DECIMAL(10,2)).
 * version: the version the client loaded (GET). Required for PUT (optimistic locking),
 * ignored for POST, where a new product always starts at version 0.
 */
public record ProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer stockQuantity,
        Integer categoryId,
        Integer supplierId,
        @Min(0) Integer version) {
}
