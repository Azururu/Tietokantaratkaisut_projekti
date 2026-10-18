package com.webstore.api.supplier;

/** primary: whether this is the product's primary supplier (products.supplier_id). */
public record SupplierResponse(
        int id,
        String name,
        String contactName,
        String phone,
        String email,
        boolean primary) {

    public static SupplierResponse from(Supplier s, boolean primary) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactName(), s.getPhone(), s.getEmail(), primary);
    }
}
