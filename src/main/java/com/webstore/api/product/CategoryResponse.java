package com.webstore.api.product;

public record CategoryResponse(int id, String name, String description) {

    static CategoryResponse from(ProductCategory c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
    }
}
